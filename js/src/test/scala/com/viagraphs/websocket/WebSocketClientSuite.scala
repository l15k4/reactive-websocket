package com.viagraphs.websocket

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Observable
import upickle._
import utest._

import scala.concurrent.Future
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSON.stringify

/**
 *
 * Each client sends its identity to server that responds with thread-id of a handler handling this connection
 * i.e. following 3 chat client connections are handled by a shared handler instance with dedicated thread
 *
 * Overall, server uses three worker threads for chat, admin and fallback (anything else that doesn't match) rest endpoints
 * i.e. localhost/chat request is handled by chat handler,  localhost/controller is handler by fallback handler
 *
 * @see [[com.viagraphs.websocket.TestingServer]]
 */
object WebSocketClientSuite extends TestSuites {

  // org.scalajs.dom.window.setTimeout(() => System.exit(0), 5000) // manual shutdown

  val controller = RxWebSocketClient(Url(WS, "localhost", 8001, Option(Endpoint.controller)))

  val integrationTests = TestSuite {

    "shared server handler instances"-{

      Observable.from(List(Endpoint.chat, Endpoint.chat, Endpoint.chat, Endpoint.admin, Endpoint.admin, Endpoint.fallback, Endpoint.controller))
        .mergeMap {
          case Endpoint.controller =>
            controller.sendAndReceive(OutMsg(stringify(literal("cmd" -> ControlMsg.closeAll))))
          case endpoint =>
            val client = RxWebSocketClient(Url(WS, "localhost", 8001, Option(endpoint)))
            1 to 2 foreach (i => client.sendAndReceive(OutMsg(stringify(literal("endpoint" -> client.url.path.get, "count" -> i, "threadId" -> "0")))))
            client.in
        }.map {
          case InMsg(ws, text) => read[TestMsg](text)
        }.buffer(100).asFuture.flatMap {
          case Some(messages) =>
            val chatMsgs = messages.filter(_.endpoint == Endpoint.chat)
            val adminMsgs = messages.filter(_.endpoint == Endpoint.admin)
            val fallbackMsgs = messages.filter(_.endpoint == Endpoint.fallback)
            assert(chatMsgs.size == 6)
            assert(adminMsgs.size == 4)
            assert(fallbackMsgs.size == 2)
            Future.successful(messages)
          case None =>
            Future.failed(new Exception("Unable to resolve server response !"))
        }
    }
  }

}