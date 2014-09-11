package com.viagraphs.websocket

import monifu.concurrent.Cancelable
import org.scalajs.dom.WebSocket
import utest._
import scala.concurrent.duration.DurationLong
import scala.scalajs.js.JSON.{stringify, parse}
import scala.scalajs.js.Dynamic.literal
import monifu.concurrent.Scheduler.Implicits.global

import scala.scalajs.js.Undefined

/**
 * @see [[com.viagraphs.websocket.TestingServer]]
 */
object WebSocketClientSuite extends TestSuite {

  def onError(error: OnError[WebSocket]) = {
    println("Error occurred : " + error.ex.getMessage)
    error.ex.printStackTrace()
    System.exit(0)
  }

  def onClose(error: OnClose[WebSocket]) = {
    println("Closing : " + (if (error.remoteOrClean) "successfully" else "with error due to : " + error.reason))
    System.exit(0)
  }

  val tests = TestSuite {

    "shared-server-handler-instances-should-have-dedicated-worker-threads"-{

      /**
       * Each client sends its identity to server that responds with thread-id of a handler handling this connection
       * i.e. following 3 chat client connections are handled by a shared handler instance with dedicated thread
       *
       * Overall, server uses three worker threads for chat, admin and fallback (anything else that doesn't match) rest endpoints
       * i.e. localhost/chat request is handled by chat handler,  localhost/controller is handler by fallback handler
       */
       List(Endpoint.chat, Endpoint.chat, Endpoint.chat, Endpoint.admin, Endpoint.admin, Endpoint.fallback)
        .map( endpoint => {
            val client = RxWebSocketClient(Url(WS, "localhost", 8001, Option(endpoint)), onClose, onError)
            1 to 10 foreach(i => client.send(stringify(literal("endpoint" -> client.url.path.get, "count" -> i))))
            client
          }
        ).foreach( client =>
          client.in.filter(
            msg => !parse(msg).selectDynamic(client.url.path.get).isInstanceOf[Undefined]
          ).foldLeft(List[String]()) {
            case (Nil, msg) =>
              msg :: Nil
            case (acc, msg) =>
              assert(acc.last == msg)
              msg :: acc
          }.foreach { acc =>
            client.url.path.get match {
              case Endpoint.chat => assert(acc.size == 30)
              case Endpoint.admin => assert(acc.size == 20)
              case Endpoint.fallback => assert(acc.size == 10)
            }
          }
        )

      val controller = RxWebSocketClient(Url(WS, "localhost", 8001, Option(Endpoint.controller)), onClose, onError)
      global.schedule(1.second, s => {
          controller.send(stringify(literal("cmd" -> ControlMsg.closeAll)))
          Cancelable()
        }
      )

    }
  }

}