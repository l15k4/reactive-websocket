package com.viagraphs.websocket

import java.net.InetSocketAddress
import java.util

import monifu.concurrent.Implicits.globalScheduler
import monifu.reactive.Ack.Continue
import monifu.reactive._
import monifu.reactive.channels.PublishChannel
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

  protected var channels = init()

  /**
   * @param msg serialized JSON message to be broadcast to all websockets
   */
  def send(msg: Outgoing): Unit = {
    channels.out.pushNext(msg)
  }

  /** @return Observable of incoming messages */
  def in = channels.in

  /** @return Observer of outgoing messages */
  def out: Channel[Outgoing] = channels.out

  def start(daemon: Boolean = false) =
    if (daemon) {
      val t = new Thread(channels.server)
      t.setDaemon(true)
      t.start()
    } else {
      channels.server.start()
    }

  /** stop server */
  def stop() = Option(channels).foreach { ch =>
    ch.server.stop()
    ch.in.pushComplete()
    ch.out.pushComplete()
  }


  def init(): Channels = {
    val incomingChannel = PublishChannel[Event[WebSocket]](BufferPolicy.BackPressured(2))
    val connectableInput = incomingChannel.publish()

    val outgoingChannel = PublishChannel[Outgoing](BufferPolicy.BackPressured(2))
    val connectableOutput = outgoingChannel.publish()

    val server = new WebSocketServer(address, 1, drafts.asJava) {

      /** A little WebSocketServer hack that allows for having a dedicated worker thread for a shared handler instance */
      val endpointWorkers = epHandlers.map(_.resourceDescriptor -> new WebSocketWorker).toMap
      endpointWorkers.foreach(_._2.start())
      override protected def queue(ws: WebSocketImpl): Unit = {
        if (ws.workerThread == null) {
          ws.workerThread = endpointWorkers.getOrElse(ws.getResourceDescriptor, decoders.iterator().next())
        }
        ws.workerThread.put(ws)
        // the only place we can get notified Server booted up successfully
        connectableInput.connect
        connectableOutput.connect
      }

      def onError(ws: WebSocket, ex: Exception): Unit = { // this is a fatal server error
        println(ex.getMessage)
        ex.printStackTrace()
        incomingChannel.pushError(ex)
        outgoingChannel.pushError(ex)
        stop()
        channels = init() // dead simple resilience
        start()
      }

      override def onWebsocketError(ws: WebSocket, ex: Exception): Unit = incomingChannel.pushNext(OnError(ws, ex))
      def onOpen(ws: WebSocket, hs: ClientHandshake): Unit = incomingChannel.pushNext(OnOpen(ws))
      def onMessage(ws: WebSocket, msg: String): Unit = incomingChannel.pushNext(InMsg(ws, msg))
      def onClose(ws: WebSocket, code: Int, reason: String, remote: Boolean): Unit = incomingChannel.pushNext(OnClose(ws, code, reason, remote))
    }

    /** Each handler is interested only in connections coming to particular rest endpoint */
    val endpointNames = epHandlers.map(_.resourceDescriptor).toSet
    epHandlers.foreach(
      h => h.handle(
        HandlerChannels(
          server,
          incomingChannel.filter(_.ws.getResourceDescriptor == h.resourceDescriptor),
          outgoingChannel
        )
      )
    )

    /** Fallback handler is interested in all connections that don't match any of declared rest endpoint */
    fbHandler.handle(
      HandlerChannels(
        server,
        incomingChannel.filter(e => !endpointNames.contains(e.ws.getResourceDescriptor)),
        outgoingChannel
      )
    )

    /** Any handler can send a message to :
      *  - all server's connections
      *  - connections coming to a specific rest endpoint
      *  - arbitrary group of connections
      *  - concrete connection
      */
    outgoingChannel.subscribe { message => // waits until `outgoing.connect()` before sending outgoing messages
      val sockets: mutable.Set[WebSocket] = server.connections().asInstanceOf[util.HashSet[WebSocket]].asScala
      message match {
        case BroadcastMsg(msg) => sockets.foreach(_.send(msg))
        case EndpointMsg(msg, endpoint) => sockets.filter(_.getResourceDescriptor == endpoint).foreach(_.send(msg))
        case GroupMsg(msg, group) => group.foreach(_.send(msg))
        case DirectMsg(msg, socket) => socket.send(msg)
      }
      Continue
    }
    Channels(server, incomingChannel, outgoingChannel)
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

case class HandlerChannels(server: WebSocketServer, in: Observable[Event[WebSocket]], out: Channel[Outgoing])
case class Channels(server: WebSocketServer, in: Channel[Event[WebSocket]], out: Channel[Outgoing])
//                                            ^ browsers calling us          ^ us broadcasting to browsers



trait Handler {
  def handle(channel: HandlerChannels): Unit
}

abstract class EndpointHandler(val endpoint: String) extends Handler {
  def resourceDescriptor: String = "/" + endpoint
}
abstract class FallbackHandler extends Handler

case class BroadcastMsg(text: String) extends Outgoing
case class EndpointMsg(text: String, endpoint: String) extends Outgoing
case class GroupMsg(text: String, group: HashSet[WebSocket]) extends Outgoing
case class DirectMsg(text: String, socket: WebSocket) extends Outgoing