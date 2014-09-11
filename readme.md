reactive-websocket
================

**Super light websocket server and browser client based on Rx**

> - **Super light** - just [scala.js][1] for client, [java-websocket][2] for server, [monifu][3] for both and a few hundreds LOscalaC
> - **based on RX** - events and I/O handled using Observables and Observers

Server
--------

Server's advantage over other implementations is that it lets you create handler instance (multiple) shared among contextually related websocket connections. Each such a handler instance has a dedicated worker thread. This allows you to keep mutable state thread-safely within that handler instance. Other implementations usually forces you to use a handler instance per websocket connection which leaves you with 4 options:

	1) keeping mutable state outside and worry about thread synchronization
	2) using Akka and re-sending events and messages to statefull actors
	3) be really RX proficient (which I'm not) to be able to deal with it the RX way
	4) work the shit around of it

Imagine you are developing a single-page-app with chat, some collaboration tool and administration. This means having 3 dedicated handler instances and one fallback instance handling arbitrary websocket connections. 4 worker threads would be assigned to those 4 handlers. `Chat handler` would handle all chat connections and in that handler you could keep state about users and rooms thread-safely. In a classic handler-per-connection scenario you create a `Chat actor` that keeps mutable state and you resend events and messages to it.

Client
--------

Client is designed exactly as server except it has no concept of handlers, just I/O - Observable/Observer. It uses [scala.js][1] to compile to javascript. There is a potential for creating client jvm implementation, however I don't think I'm going to need it.

```sequence
ChatClient1->ChatHandler:
ChatClient2->ChatHandler:
ChatClient3->ChatHandler:
AdminClient1->AdminHandler:
AdminClient2->AdminHandler:
AdminHandler-->AdminClient1:
AdminHandler-->AdminClient2:
ChatHandler-->ChatClient1:
ChatHandler-->ChatClient2:
ChatHandler-->ChatClient3:
```


  [1]: http://www.scala-js.org/
  [2]: https://github.com/TooTallNate/Java-WebSocket
  [3]: https://github.com/monifu