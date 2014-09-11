package com.viagraphs.websocket

import upickle._

/** Supposed to be started via 'startTestServer' task in 'jvm' project from 'js' project for running scalajs test suite
  *
  * Each of these handlers is an instance shared by multiple websocket connections and therefore it has a dedicated worker thread
  * It means that you are supposed to keep mutable state within handlers only. Mutating outside state from handlers would not be thread-safe
  *
  * This server just replies to messages with thread-id, client test suites validates that each handler has a dedicated thread
  */
object TestingServer extends App {

  def getThreadIdAsJson(endpoint: String): String = s"""{"$endpoint" : ${Thread.currentThread().getId}}"""

  val fallbackHandler = new FallbackHandler {
    override def handle(channel: Channel): Unit = {
      channel.in
        .dump(Endpoint.fallback + "Handler")
        .foreach {
        case OnMessage(ws, msg) =>
          val endpoint = read[TestMsg](msg).endpoint
          assert(endpoint == Endpoint.fallback, Endpoint.fallback + " handler does not accept message from endpoint : " + endpoint)
          ws.send(getThreadIdAsJson(endpoint))
        case _ =>
      }
    }
  }

  val chatHandler = new EndpointHandler(Endpoint.chat) {

    // here is a space for mutating state thread-safely, for instance who belongs to which char room etc.

    def handle(channel: Channel): Unit = {
      channel.in
        .dump(Endpoint.chat + "Handler")
        .foreach {
        case OnMessage(ws, msg) =>
          val endpoint = read[TestMsg](msg).endpoint
          assert(endpoint == Endpoint.chat, Endpoint.chat + " handler does not accept message from endpoint : " + endpoint)
          ws.send(getThreadIdAsJson(endpoint))
        case _ =>
      }
    }
  }

  val adminHandler = new EndpointHandler(Endpoint.admin) {
    def handle(channel: Channel): Unit = {
      channel.in
        .dump(Endpoint.admin + "Handler")
        .foreach {
        case OnMessage(ws, msg) =>
          val endpoint = read[TestMsg](msg).endpoint
          assert(endpoint == Endpoint.admin, Endpoint.admin + " handler does not accept message from endpoint : " + endpoint)
          ws.send(getThreadIdAsJson(endpoint))
        case _ =>
      }
    }
  }

  /**  handler listening to commands regarding lifecycle of this test suite  */
  val controller = new EndpointHandler(Endpoint.controller) {
    def handle(channel: Channel): Unit = {
      channel.in
        .dump(Endpoint.controller + "Handler")
        .foreach {
        case OnMessage(ws, msg) =>
          read[ControlMsg](msg).cmd match {
            case ControlMsg.closeAll => channel.server.stop()
        }
        case _ =>
      }
    }
  }

  RxWebSocketServer(fallbackHandler, List(adminHandler, chatHandler, controller)).start()

}
