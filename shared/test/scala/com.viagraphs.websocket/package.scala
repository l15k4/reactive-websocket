package com.viagraphs

package object websocket {

  case class ControlMsg(cmd: String)
  case class TestMsg(endpoint: String, count: Int)

  object ControlMsg {
    val closeAll = "closeAll"
  }

  object Endpoint {
    val fallback = "fallback"
    val chat = "chat"
    val admin = "admin"
    val controller = "controller"
  }

}
