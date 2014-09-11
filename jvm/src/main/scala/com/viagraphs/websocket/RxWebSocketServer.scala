package com.viagraphs.websocket

import java.net.InetSocketAddress
import java.util

import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.api.Ack.Continue
import monifu.reactive.subjects.{ConnectableSubject, PublishSubject}
import monifu.reactive.{Observer, Subject}

import org.java_websocket.drafts.Draft
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.java_websocket.{WebSocket, WebSocketImpl}

import scala.collection.JavaConverters._
import scala.collection.immutable.HashSet
import scala.collection.mutable

/**
 * A reactive websocket server
 * @param fbHandler is a shared handler instance with dedicated worker thread for all connections that don't match
 *                 restful endpoints (http://localhost:8001/endpoint) of endpoint handlers
 * @param epHandlers endpoint handler is a shared instance with dedicated worker thread for all connections whose endpoint (ws.getResourceDescriptor)
 *                   matches handler's endpoint
 * @param drafts supported websocket specification versions
 */
class RxWebSocketServer(fbHandler: FallbackHandler, epHandlers: List[EndpointHandler], address: InetSocketAddress, drafts: List[Draft]) {

  protected var channel = init()

  /**
   * @param msg serialized JSON message to be broadcast to all websockets
   */
  def send(msg: OutgoingMsg): Unit = {
    channel.out.onNext(msg)
  }

  /** @return Observable of incoming messages */
  def in = channel.in

  /** @return Observer of outgoing messages */
  def out: Observer[OutgoingMsg] = channel.out

  def start(daemon: Boolean = false) =
    if (daemon) {
      val t = new Thread(channel.server)
      t.setDaemon(true)
      t.start()
    } else {
      channel.server.start()
    }

  /** stop server */
  def stop() = Option(channel).foreach { ch =>
    ch.server.stop()
    ch.in.onComplete()
    ch.out.onComplete()
  }

  def init(): Channel = {
    val incoming = PublishSubject[Event[WebSocket]]().replay()
    val outgoing = PublishSubject[OutgoingMsg]().replay()
    val server = new WebSocketServer(address, 1, drafts.asJava) {

      /** A little WebSocketServer hack that allows for having a dedicated worker thread for a shared handler instance */
      val endpointWorkers = epHandlers.map(_.resourceDescriptor -> new WebSocketWorker).toMap
      endpointWorkers.foreach(_._2.start())
      override protected def queue(ws: WebSocketImpl) {
        if (ws.workerThread == null) {
          ws.workerThread = endpointWorkers.getOrElse(ws.getResourceDescriptor, decoders.iterator().next())
        }
        ws.workerThread.put(ws)
        // the only place we can get notified Server booted up successfully
        incoming.connect()
        outgoing.connect()
      }

      def onError(ws: WebSocket, ex: Exception): Unit = { // this is a fatal server error
        println(ex.getMessage)
        ex.printStackTrace()
        incoming.onError(ex)
        outgoing.onError(ex)
        stop()
        channel = init() // dead simple resilience
        start()
      }

      override def onWebsocketError(ws: WebSocket, ex: Exception): Unit = incoming.onNext(OnError(ws, ex))
      def onOpen(ws: WebSocket, hs: ClientHandshake): Unit = incoming.onNext(OnOpen(ws))
      def onMessage(ws: WebSocket, msg: String): Unit = incoming.onNext(OnMessage(ws, msg))
      def onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean): Unit = incoming.onNext(OnClose(ws, code, reason, remote))
    }

    /** Each handler is interested only in connections coming to particular rest endpoint */
    val endpointNames = epHandlers.map(_.resourceDescriptor).toSet
    epHandlers.foreach(
      h => h.handle(
        Channel(
          server,
          incoming.filter(_.ws.getResourceDescriptor == h.resourceDescriptor),
          outgoing
        )
      )
    )

    /** Fallback handler is interested in all connections that don't match any of declared rest endpoint */
    fbHandler.handle(
      Channel(
        server,
        incoming.filter(e => !endpointNames.contains(e.ws.getResourceDescriptor)),
        outgoing
      )
    )

    /** Any handler can send a message to :
      *  - all server's connections
      *  - connections coming to a specific rest endpoint
      *  - arbitrary group of connections
      *  - concrete connection
      */
    outgoing.subscribe { message => // waits until `outgoing.connect()` before sending outgoing messages
      val sockets: mutable.Set[WebSocket] = server.connections().asInstanceOf[util.HashSet[WebSocket]].asScala
      message match {
        case BroadcastMsg(msg) => sockets.foreach(_.send(msg))
        case EndpointMsg(msg, endpoint) => sockets.filter(_.getResourceDescriptor == endpoint).foreach(_.send(msg))
        case GroupMsg(msg, group) => group.foreach(_.send(msg))
        case DirectMsg(msg, socket) => socket.send(msg)
      }
      Continue
    }
    Channel(server, incoming, outgoing)
  }
}

object RxWebSocketServer {
  def apply(
         fallback: FallbackHandler,
         handlers: List[EndpointHandler] = List(),
         address: InetSocketAddress = new InetSocketAddress(8001),
         drafts: List[Draft] = List()
       ): RxWebSocketServer
    = new RxWebSocketServer(fallback, handlers, address, drafts)
}

case class Channel(server: WebSocketServer, in: Subject[Event[WebSocket],Event[WebSocket]], out: ConnectableSubject[OutgoingMsg, OutgoingMsg])
//                                          ^ browsers calling us                                       ^ us broadcasting to browsers

trait Handler {
  def handle(channel: Channel)
}

abstract class EndpointHandler(val endpoint: String) extends Handler {
  def resourceDescriptor: String = "/" + endpoint
}
abstract class FallbackHandler extends Handler

sealed trait OutgoingMsg { def msg: String }
case class BroadcastMsg(msg: String) extends OutgoingMsg
case class EndpointMsg(msg: String, endpoint: String) extends OutgoingMsg
case class GroupMsg(msg: String, group: HashSet[WebSocket]) extends OutgoingMsg
case class DirectMsg(msg: String, socket: WebSocket) extends OutgoingMsg