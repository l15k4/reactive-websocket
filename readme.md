## reactive-websocket

**(Legacy software - not maintained anymore due to many breaking changes in Monix and java-websocket)**


[![Build Status](https://travis-ci.org/pragmaxim/reactive-websocket.svg)](https://travis-ci.org/pragmaxim/reactive-websocket)

* dependencies 
    * server : `"com.pragmaxim" %%% "reactive-websocket-server" % "0.0.4-SNAPSHOT"`
    * client : `"com.pragmaxim" %%% "reactive-websocket-client" % "0.0.4-SNAPSHOT"`
* Scala.js version : 0.6.31


NOTE: 
* This project is still waiting on accepting a pull request in [java-websocket][2], it'll work with my fork of it only

**Light websocket server and browser client based on Rx principles**

> - **Super light** - just [scala.js][1] for client, [java-websocket][2] for server, [monifu][3] for both and a few hundreds LOscalaC
> - **based on RX** - events and I/O handled using Observables and Observers

### Server


Server's advantage over other implementations is that it lets you create handler instance (multiple) shared among contextually related websocket connections. Each such a handler instance has a dedicated worker thread. This allows you to keep mutable state thread-safely within that handler instance. Other implementations usually force you to use a handler instance per websocket connection which leaves you with 4 options:

	1) keeping mutable state outside and worry about thread synchronization
	2) using Akka and re-sending events and messages to statefull actors
	3) be really RX proficient (which I'm not) to be able to deal with it the RX way
	4) work the shit around of it

Imagine you are developing a single-page-app with chat, some collaboration tool and administration. This means having 3 dedicated handler instances and one fallback instance handling arbitrary websocket connections. 4 worker threads would be assigned to those 4 handlers. `Chat handler` would handle all chat connections and in that handler you could keep state about users and rooms thread-safely. In a classic handler-per-connection scenario you create a `Chat actor` that keeps mutable state and you resend events and messages to it.

### Client


Client is designed exactly as server except it has no concept of handlers, just I/O - Observable/Observer. It uses [scala.js][1] to compile to javascript. There is a potential for creating client jvm implementation, however I don't think I'm going to need it.


USAGE:

Please see WebSocketClientSuite and TestingServer for inspiration

  [1]: http://www.scala-js.org/
  [2]: https://github.com/TooTallNate/Java-WebSocket
  [3]: https://github.com/monifu