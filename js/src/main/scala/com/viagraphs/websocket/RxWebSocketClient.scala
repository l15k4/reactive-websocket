package com.viagraphs.websocket

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive._
import monifu.reactive.channels.{PublishChannel, SubjectChannel}
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.subjects.ReplaySubject
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, ErrorEvent, MessageEvent, WebSocket}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js.JSON._

/**
 * A reactive websocket wrapper
 */
class RxWebSocketClient(val url: Url)(implicit scheduler: Scheduler) {

  case class Channels(
                lc: Subject[Event[WebSocket], Event[WebSocket]], // websocket lifecycle events(open,close,error)
                in: SubjectChannel[InMsg[WebSocket],InMsg[WebSocket]], // browser calling us
                out: SubjectChannel[OutMsg,OutMsg] // us calling browser
              )

  protected val channels = init()

  /**
   * @param msg serialized JSON message to be sent out``
   * @return Observable stream of incoming messages
   */
  def sendAndReceive(msg: OutMsg): Observable[InMsg[WebSocket]] = {
    channels.out.pushNext(msg)
    channels.in
  }

  /** @return Observable of incoming messages */
  def in: Observable[InMsg[WebSocket]] = channels.in

  /** @return Observer of outgoing messages */
  def out: Channel[OutMsg] = channels.out

  /** @return Observer of lifecyle events (open, close, error) that are replayed from the beginning */
  def lifecycle: Observable[Event[WebSocket]] = channels.lc

  /** close websocket */
  def stop() = channels.lc.onComplete()

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
  private def init(): Channels = {
    var ws = new WebSocket(url.stringify)

    val outgoingChannel = PublishChannel[OutMsg](BufferPolicy.BackPressured(2))
    val lifecycleSubject = ReplaySubject[Event[WebSocket]]()
    val incomingChannel = PublishChannel[InMsg[WebSocket]](BufferPolicy.BackPressured(2))
    outgoingChannel.subscribe { msg =>
        val promise = Promise[Ack]()
        def send(m: String, attempt: Int = 0): Unit = ws.readyState match {
          case WebSocket.CLOSING =>
            promise.tryCompleteWith(Cancel)
          case WebSocket.CLOSED =>
            if (attempt < 10) {
              ws = new WebSocket(url.stringify)
              listen(ws, lifecycleSubject, incomingChannel)
              dom.window.setTimeout(() => send(m, attempt + 1), 500)
            } else {
              promise.tryCompleteWith(Cancel)
            }
          case WebSocket.OPEN =>
            ws.send(msg.text)
            promise.tryCompleteWith(Continue)
          case WebSocket.CONNECTING =>
            if (attempt < 10) {
              dom.window.setTimeout(() => send(m, attempt + 1), 500)
            } else {
              promise.tryCompleteWith(Cancel)
            }
        }
        send(msg.text)
      promise.future
    }

    val connectableOutput = outgoingChannel.publish()
    val connectableInput = incomingChannel.publish()

    lifecycleSubject.subscribe(
      new Observer[Event[WebSocket]] {
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
        def onComplete(): Unit = {
          println("Connection completed locally")
          outgoingChannel.pushComplete()
          incomingChannel.pushComplete()
        }

        def onNext(e: Event[WebSocket]): Future[Ack] = e match {
          case OnOpen(_) =>
            connectableInput.connect
            connectableOutput.connect
            Continue
          case oc @ OnClose(_, code, reason, clean) =>
            println("Connection completed remotely")
            outgoingChannel.pushComplete()
            incomingChannel.pushComplete()
            Continue // we don't care about Close, stop() is a proper way of shutting this down
          case er @ OnError(_, ex) =>
            println(ex)
            println("reconnecting websocket")
            ws = new WebSocket(url.stringify)
            listen(ws, lifecycleSubject, incomingChannel)
            Continue
          case x => throw new IllegalStateException("Lifecycle yields : " + x)
        }
      }
    )
    listen(ws, lifecycleSubject, incomingChannel)
    Channels(lifecycleSubject, incomingChannel, outgoingChannel)
  }

  def listen(ws: WebSocket, lc: Subject[Event[WebSocket], Event[WebSocket]], in: SubjectChannel[InMsg[WebSocket],InMsg[WebSocket]]): Unit = {
    ws.onmessage = (x: MessageEvent) => in.pushNext(InMsg(ws, x.data.toString))
    ws.onopen = (x: dom.Event) => lc.onNext(OnOpen(ws))
    ws.onclose = (x: CloseEvent) => lc.onNext(OnClose(ws, x.code, x.reason, x.wasClean))
    ws.onerror = (x: ErrorEvent) => lc.onNext(OnError(ws, new Exception(x.message)))
  }

  implicit class ObservableExtensions[T <: Msg](obs: Observable[T]) {

    def dumpJson(prefix: String, color: String = Console.YELLOW): Observable[T] = {
      def <--> =  color + prefix + Console.RESET
      Observable.create { subscriber =>
        val observer = subscriber.observer
        obs.unsafeSubscribe(
          new Observer[T] {
            private[this] var pos = 0

            def onNext(elem: T): Future[Ack] = {
              println(s"$pos: ${<-->}${stringify(parse(elem.text), space = 2)}")
              pos += 1
              val f = observer.onNext(elem)
              f.onCancel { pos += 1; println(s"$pos: ${<-->}canceled") }
              f
            }

            def onError(ex: Throwable) = {
              println(s"$pos: ${<-->}$ex")
              pos += 1
              observer.onError(ex)
            }

            def onComplete() = {
              println(s"$pos: ${<-->}completed")
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
  def apply(url: Url)(implicit scheduler: Scheduler): RxWebSocketClient =
    new RxWebSocketClient(url)
}

case class OutMsg(text: String) extends Outgoing