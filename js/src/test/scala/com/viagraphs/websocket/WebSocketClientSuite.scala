package com.viagraphs.websocket

import scala.collection.mutable.ListBuffer
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.JSON.{parse, stringify}
import scala.scalajs.js.Undefined
import scala.scalajs.test.JasmineTest

/**
 * @see [[com.viagraphs.websocket.TestingServer]]
 */
object WebSocketClientSuite extends JasmineTest {

  describe("shared server handler instances") {
    beforeEach { jasmine.Clock.useMock() }

    /**
     * Each client sends its identity to server that responds with thread-id of a handler handling this connection
     * i.e. following 3 chat client connections are handled by a shared handler instance with dedicated thread
     *
     * Overall, server uses three worker threads for chat, admin and fallback (anything else that doesn't match) rest endpoints
     * i.e. localhost/chat request is handled by chat handler,  localhost/controller is handler by fallback handler
     */
    it("should have dedicated worker threads") {
      import monifu.concurrent.Scheduler.Implicits.trampoline
      val chatMessages = ListBuffer[String]()
      val adminMessages = ListBuffer[String]()
      val fallbackMessages = ListBuffer[String]()

      List(Endpoint.chat, Endpoint.chat, Endpoint.chat, Endpoint.admin, Endpoint.admin, Endpoint.fallback)
        .map(endpoint => {
            val client = RxWebSocketClient(Url(WS, "localhost", 8001, Option(endpoint)))
            1 to 2 foreach (i => client.sendAndReceive(OutMsg(stringify(literal("endpoint" -> client.url.path.get, "count" -> i)))))
            client
          }
        ).foreach( client =>
          client.in
            .filter(msg => !parse(msg.text).selectDynamic(client.url.path.get).isInstanceOf[Undefined])
            .foreach { acc =>
            client.url.path.get match {
              case Endpoint.chat => chatMessages += acc.text
              case Endpoint.admin => adminMessages += acc.text
              case Endpoint.fallback => fallbackMessages += acc.text
            }
          }
        )

      jasmine.Clock.tick(500)
      expect(chatMessages.size).toBe(6)
      expect(adminMessages.size).toBe(4)
      expect(fallbackMessages.size).toBe(2)

      val controller = RxWebSocketClient(Url(WS, "localhost", 8001, Option(Endpoint.controller)))
      controller.sendAndReceive(OutMsg(stringify(literal("cmd" -> ControlMsg.closeAll))))
      controller.lifecycle.foreach {
        case OnError(_, ex) =>
          println("Error occurred : " + ex.getMessage)
          ex.printStackTrace()
          System.exit(0)
        case OnClose(_, _, reason, clean) =>
          println("Closing : " + (if (clean) "successfully" else "with error due to : " + reason))
          System.exit(0)
        case _ =>
      }

      jasmine.Clock.tick(500)
    }
  }

}