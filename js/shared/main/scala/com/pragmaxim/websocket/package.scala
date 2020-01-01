package com.pragmaxim

import monix.execution.Scheduler
import monix.execution.Ack
import monix.execution.Ack.{Stop, Continue}

import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal


package object websocket {

  implicit class FuturePimp(val source: Future[Ack]) extends AnyVal {
    /**
     * Triggers execution of the given callback, once the source terminates either
     * with a `Stop` or with a failure.
     */
    def onCancel(cb: => Unit)(implicit s: Scheduler): Future[Ack] =
      source match {
        case Continue => source
        case Stop =>
          try cb catch {
            case NonFatal(ex) => s.reportFailure(ex)
          }
          source
        case sync if sync.isCompleted =>
          sync.value.get match {
            case Continue.AsSuccess => source
            case Stop.AsSuccess | Failure(_) =>
              try cb catch {
                case NonFatal(ex) => s.reportFailure(ex)
              }
              source
            case other =>
              // branch not necessary, but Scala's compiler emits warnings if missing
              s.reportFailure(new MatchError(other.toString))
              source
          }
        case async =>
          source.onComplete {
            case Stop.AsSuccess | Failure(_) => cb
            case _ => // nothing
          }
          source
      }

  }

}