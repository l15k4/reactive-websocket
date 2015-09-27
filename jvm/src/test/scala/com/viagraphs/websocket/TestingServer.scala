package com.viagraphs.websocket

import org.java_websocket.framing.CloseFrame
import upickle._
import monifu.concurrent.Implicits.globalScheduler
import scala.collection.JavaConverters._

import scala.concurrent.duration._


/** Supposed to be started via 'startTestServer' task in 'jvm' project from 'js' project for running scalajs test suite
  *
  * Each of these handlers is an instance shared by multiple websocket connections and therefore it has a dedicated worker thread
  * It means that you are supposed to keep mutable state within handlers only. Mutating outside state from handlers would not be thread-safe
  *
  * This server just replies to messages with thread-id, client test suites validates that each handler has a dedicated thread
  */
object TestingServer extends App {

  val fallbackHandler = new FallbackHandler {
    override def handle(channel: HandlerChannels): Unit = {
      channel.in
        .dump(Endpoint.fallback + "Handler")
        .foreach {
        case InMsg(ws, msg) =>
          val testMsg = read[TestMsg](msg)
          assert(testMsg.endpoint == Endpoint.fallback, Endpoint.fallback + " handler does not accept message from endpoint : " + testMsg.endpoint)
          val response = write[TestMsg](testMsg.copy(threadId = Thread.currentThread().getId.toString))
          ws.send(response)
        case _ =>
      }
    }
  }

  val chatHandler = new EndpointHandler(Endpoint.chat) {

    // here is a space for mutating state thread-safely, for instance who belongs to which char room etc.

    def handle(channel: HandlerChannels): Unit = {
      channel.in
        .dump(Endpoint.chat + "Handler")
        .foreach {
        case InMsg(ws, msg) =>
          val testMsg = read[TestMsg](msg)
          assert(testMsg.endpoint == Endpoint.chat, Endpoint.chat + " handler does not accept message from endpoint : " + testMsg.endpoint)
          val response = write[TestMsg](testMsg.copy(threadId = Thread.currentThread().getId.toString))
          ws.send(response)
        case _ =>
      }
    }
  }

  val adminHandler = new EndpointHandler(Endpoint.admin) {
    def handle(channel: HandlerChannels): Unit = {
      channel.in
        .dump(Endpoint.admin + "Handler")
        .foreach {
        case InMsg(ws, msg) =>
          val testMsg = read[TestMsg](msg)
          assert(testMsg.endpoint == Endpoint.admin, Endpoint.admin + " handler does not accept message from endpoint : " + testMsg.endpoint)
          val response = write[TestMsg](testMsg.copy(threadId = Thread.currentThread().getId.toString))
          ws.send(response)
        case _ =>
      }
    }
  }

  /**  handler listening to commands regarding lifecycle of this test suite  */
  val controller = new EndpointHandler(Endpoint.controller) {
    def handle(channel: HandlerChannels): Unit = {
      channel.in
        .dump(Endpoint.controller + "Handler")
        .foreach {
        case InMsg(ws, msg) =>
          read[ControlMsg](msg).cmd match {
            case ControlMsg.closeAll =>
              globalScheduler.scheduleOnce(500.millis) {
                println(Console.CYAN + "STOPPING SERVER" + Console.RESET)

                val connections = channel.server.connections()
                connections.synchronized {
                  connections.asScala.toList.foreach(_.close(CloseFrame.GOING_AWAY))
                }
              }
        }
        case _ =>
      }
    }
  }

  RxWebSocketServer(fallbackHandler, List(adminHandler, chatHandler, controller)).start()

}
