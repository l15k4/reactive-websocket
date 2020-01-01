package websocket

case class TestMsg(endpoint: String, count: Int, threadId: String)

case class ControlMsg(cmd: String)
object ControlMsg {
  val closeAll = "closeAll"
}

object Endpoint {
  val fallback = "fallback"
  val chat = "chat"
  val admin = "admin"
  val controller = "controller"
}

