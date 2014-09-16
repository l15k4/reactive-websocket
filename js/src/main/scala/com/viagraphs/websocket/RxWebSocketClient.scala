package com.viagraphs.websocket

import monifu.concurrent.Scheduler
import monifu.reactive.Ack.{Cancel, Continue}
import monifu.reactive.channels.SubjectChannel
import monifu.reactive.internals.FutureAckExtensions
import monifu.reactive.subjects.PublishSubject
import monifu.reactive._
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, ErrorEvent, MessageEvent, WebSocket}

import scala.annotation.tailrec
import scala.concurrent.Future
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

  protected var channels = init()
  //             ^ the only mutable state initialized on client start and reinitialized on WebSocket error

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
    val ws = new WebSocket(url.stringify)
    val connectableOutput = PublishSubject[OutMsg]().multicast(PublishSubject().lift(o => o.dumpJson("client -->", Console.RED)))
    val outgoingChannel = SubjectChannel(connectableOutput, BufferPolicy.BackPressured(2))
    outgoingChannel.subscribe {
      msg =>
        @tailrec def send(m: String, attempt: Int = 0): Future[Ack] = ws.readyState match {
          case WebSocket.CLOSING => Cancel
          case WebSocket.CLOSED => Cancel
          case WebSocket.OPEN =>
            ws.send(msg.text)
            Continue
          case WebSocket.CONNECTING =>
            if (attempt < 15)
              send(m, attempt + 1)
            else
              Cancel
        }
        send(msg.text)
    }

    val connectableInput = PublishSubject[InMsg[WebSocket]]().multicast(PublishSubject().lift(o => o.dumpJson("client <--", Console.GREEN)))
    val incomingChannel = SubjectChannel(connectableInput, BufferPolicy.BackPressured(5))
    ws.onmessage = (x: MessageEvent) => incomingChannel.pushNext(InMsg(ws, x.data.toString))

    val lifecycle = PublishSubject[Event[WebSocket]]().replay()
    ws.onopen = (x: dom.Event) => lifecycle.onNext(OnOpen(ws))
    ws.onclose = (x: CloseEvent) => lifecycle.onNext(OnClose(ws, x.code, x.reason, x.wasClean))
    ws.onerror = (x: ErrorEvent) => lifecycle.onNext(OnError(ws, new Exception(x.message)))

    lifecycle.subscribe(
      new Observer[Event[WebSocket]] {
        def onError(ex: Throwable): Unit = scheduler.reportFailure(ex)
        def onComplete(): Unit = {
          outgoingChannel.pushComplete()
          incomingChannel.pushComplete()
        }

        def onNext(e: Event[WebSocket]): Future[Ack] = e match {
          case OnOpen(_) =>
            connectableInput.connect()
            connectableOutput.connect()
            Continue
          case oc @ OnClose(_, code, reason, clean) =>
            outgoingChannel.pushComplete()
            incomingChannel.pushComplete()
            Cancel
          case er @ OnError(_, ex) =>
            outgoingChannel.pushError(ex)
            incomingChannel.pushError(ex)
            channels = init()
            Cancel
          case x => throw new IllegalStateException("Lifecycle yields : " + x)
        }
      }
    )

    lifecycle.connect()

    Channels(lifecycle, incomingChannel, outgoingChannel)
  }

  implicit class ObservableExtensions[T <: Msg](obs: Observable[T]) {

    def dumpJson(prefix: String, color: String = Console.YELLOW): Observable[T] = {
      def <--> =  color + prefix + Console.RESET
      Observable.create { observer =>
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