package com.viagraphs.websocket

case class Url(protocol: Protocol, host: String, port: Int, path: Option[String]) {
  def stringify = s"${protocol.value()}://$host:$port/${path.getOrElse("")}"
}

sealed trait Protocol {def value(): String}
case object WS extends Protocol {val value = "ws"}
case object WSS extends Protocol {val value = "wss"}

sealed trait Event[WS] { def ws: WS }
case class OnOpen[WS](ws: WS) extends Event[WS]
case class OnError[WS](ws: WS, ex: Throwable) extends Event[WS]
case class OnClose[WS](ws: WS, code: Int, reason: String, remoteOrClean: Boolean) extends Event[WS]
case class OnMessage[WS](ws: WS, msg: String) extends Event[WS]
