package com.viagraphs.websocket

import monifu.concurrent.Scheduler.Implicits.global
import monifu.reactive.api.Ack
import monifu.reactive.api.Ack.{Cancel, Continue}
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.observers.SafeObserver
import monifu.reactive.subjects.{ConnectableSubject, PublishSubject, ReplaySubject}
import monifu.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, ErrorEvent, MessageEvent, WebSocket}
import scala.scalajs.js.JSON._

import scala.concurrent.Future

/**
 * A reactive websocket wrapper
 */
class RxWebSocketClient(val url: Url, onClose: OnClose[WebSocket] => Unit, onError: OnError[WebSocket] => Unit) {

  case class Channel(ws: WebSocket, in: Observable[String], out: ConnectableSubject[String,String])
  //                                 ^ browser calling us    ^ us calling browser

  protected var channel = init()
  //            ^ the only mutable state initialized on client start and reinitialized on WebSocket error

  /**
   * @param msg serialized JSON message to be sent out
   * @return Observable stream of incoming messages
   */
  def send(msg: String): Observable[String] = {
    channel.out.onNext(msg)
    channel.in
  }

  /** @return Observable of incoming messages */
  def in = channel.in

  /** @return Observer of outgoing messages */
  def out: Observer[String] = channel.out

  /** close websocket */
  def stop() = channel.ws.close(1000, "")

  /**
   * Creates :
   *   1. Connectable PublishSubject that :
   *     - subscribes observer of outgoing messages
   *     - starts feeding it when subject connects (websocket's onopen event)
   *
   *   2. Observable of incoming messages
   *
   *   3. Observable of websocket lifecycle events that :
   *     - makes WebSocketChannel resilient by recovering from websocket errors
   *     - connects outgoing subject when websocket is ready
   *     - properly shut WebSocketChannel down when websocket closes
   *
   * @note that traffic of all 3 Observables is being logged (dump/dumpJson) until a stable release
   */
  private def init(): Channel = {
    val ws = new WebSocket(url.stringify)
    val outgoing = PublishSubject[String]().multicast(ReplaySubject().lift(o => o.dumpJson("OUTGOING")))
    outgoing.subscribe {  // waits until `outgoing.connect()` before sending outgoing messages
      msg =>
        ws.send(msg) // handling just onNext, there is nothing we can do about error or complete
        Continue
    }

    val incoming = Observable.create[String] { o =>
      val so = SafeObserver(o)
      ws.onmessage = (x: MessageEvent) => {
        so.onNext(x.data.toString)
      }
    }.dumpJson("INCOMING")

    Observable.create[Event[WebSocket]] { o =>
      val so = SafeObserver(o)
      ws.onopen = (x: dom.Event) => so.onNext(OnOpen(ws))
      ws.onclose = (x: CloseEvent) => so.onNext(OnClose(ws, x.code, x.reason, x.wasClean))
      ws.onerror = (x: ErrorEvent) => so.onNext(OnError(ws, new Exception(x.message)))
    }.dump("LIFECYCLE").subscribe(_ match {
        case OnOpen(_) =>
          outgoing.connect()
          Continue
        case oc @ OnClose(_, code, reason, clean) =>
          outgoing.onComplete()
          onClose(oc)
          Cancel
        case er @ OnError(_, ex) =>
          outgoing.onError(ex) // Problem - WebSocket Error is followed by Close, but RX allows only one of them
          onError(er)
          channel = init()     // Solution - Cancel and reinitialize on Error and do not care about latter WebSocket Close
          Cancel  //  ^  on WebSocket Error we let user space keep reference on this instance, we just reinitialize it
        case x => throw new IllegalStateException("Lifecycle yields : " + x)
        }
    )
    Channel(ws, incoming, outgoing)
  }

  implicit class ObservableExtensions(obs: Observable[String]) {

    def dumpJson(prefix: String): Observable[String] = {
      Observable.create { observer =>
        obs.unsafeSubscribe(
          new Observer[String] {
            private[this] var pos = 0

            def onNext(elem: String): Future[Ack] = {
              println(s"$pos: $prefix-->${stringify(parse(elem), space = 2)}")
              pos += 1
              val f = observer.onNext(elem)
              f.onCancel { pos += 1; println(s"$pos: $prefix-->canceled") }
              f
            }

            def onError(ex: Throwable) = {
              println(s"$pos: $prefix-->$ex")
              pos += 1
              observer.onError(ex)
            }

            def onComplete() = {
              println(s"$pos: $prefix-->completed")
              pos += 1
              observer.onComplete()
            }
          }
        )
      }
    }
  }
}

object RxWebSocketClient {
  def apply(url: Url, onClose: OnClose[WebSocket] => Unit, onError: OnError[WebSocket] => Unit): RxWebSocketClient =
    new RxWebSocketClient(url, onClose, onError)
}