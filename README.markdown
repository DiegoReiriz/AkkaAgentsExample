# Crash into akka

Small documentation with my first steps into the akka framework that's made possible use the Agent Model paradigm in the Scala Language

## Options, options and more options

I need to made a multi-threading application in wich thoose threads have to send messages between them. I went trough multiple options like do it all my self using plain sockets and Threads, but the cost of maintenance of a development like that could be very high. After that I thought about a RPC aproach and I was almost decided, but i found that the akka framework has a great compatibility with the project Apache Kafka, wich i use as a message broker in that application, so i finally decided to use akka as a long term bet of using it with Apache Kafka Streams into a Future.

With this selection I'm aware that the cost of a framework like akka is higher than the cost of a RPC framework like Fineagle(Looks like a cool project made by Twitter).

## What is akka?

So if you check the [akka](http://akka.io/) project webpage, you can see a really fancy definition of the framework, but to keep it simple, it's just a framework that made really easy for developers to develop distributed applications using the [Actor model](https://en.wikipedia.org/wiki/Actor_model)

## Time to get dirty

I think that the best way to understand what is akka and how it works it doing a simple example, so let's develop and application in which we have 2 kinds of agents, *Producers* and *Consumers*. The idea is that, the *Producers* will send a message through a socket that will be listening a *Consumer* agent. Once the *Consumer* agent recieves the data, it will print the message.

So with this example, we are going to learn how to send message to actor using akka and how to write and read from sockets,

```
         Producer    Consumer
            * --------> *
```

### Let's start building the producer

The producer agent that we are building it's an easy one. The agent will do the following steps:

1. Agent check if can stablish a connection to a socket
2. Agent sends data trough the socket
3. Agent close the connection
4. Agent destroys itself

For this puspose, we are going to use Scala companion objects.

First of all we have to create the following Boilerplate in a file that I'm gonna name **SocketProducer.scala**

```scala
import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString

import scala.collection.mutable

object SocketProducer {
  //Needed to create an Agent with a given configuration
  def props(host: String, port: Int, messages: mutable.MutableList[String]): Props =
    Props(new SocketProducer(host,port,messages))

}

class SocketProducer(host: String, port: Int, messages: mutable.MutableList[String]) extends Actor with ActorLogging{

  import SocketProducer._

  // function that allows us to do some logic before the Agent is up
  override def preStart(): Unit = {
    log.info("Starting socket PRODUCER actor with following config {}:{}",host,port)
  }

  // function that allows us to do some logic once the agent is down
  override def postStop(): Unit = {
    log.info("Stopped socket PRODUCER actor")
  }

}

```

Now we need to connect the agent to the socket, for that purppose we have to communicate our agent with the IO agent that manages the communication with the Socket and we also have to implement the receive method of our agent, because the IO agent will send use message with information about the socket.

```scala
import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString

import scala.collection.mutable

object SocketProducer {

  //Needed to create an Agent with a given configuration
  def props(host: String, port: Int, messages: mutable.MutableList[String]): Props =
    Props(new SocketProducer(host,port,messages))

}

class SocketProducer(host: String, port: Int, messages: mutable.MutableList[String]) extends Actor with ActorLogging{

  import SocketProducer._

  import akka.io.Tcp._
  import context.system

  // agent that manages the low level communication with the socket
  // this agent can send messages to out SocketProducer Agent, because
  // a reference to the SocketProducer agent is implicit passed
  // when we invoke IO(Tcp)
  IO(Tcp) ! Connect(new InetSocketAddress(host,port))

  // function that allows us to do some logic before the Agent is up
  override def preStart(): Unit = {
    log.info("Starting socket PRODUCER actor with following config {}:{}",host,port)
  }

  // function that allows us to do some logic once the agent is down
  override def postStop(): Unit = {
    log.info("Stopped socket PRODUCER actor")
  }

  override def receive = {
    case CommandFailed(_: Connect) =>
      context stop self

    //Needed to establish a connection
    case c @ Connected(remote, local) =>

      //opens connection
      val connection = sender()
      //establish connection
      connection ! Register(self)
      //sends messages
      messages.foreach(message => {
        log.info("Trying to send message: "+message)
        connection ! Write(ByteString(message))
      })
      //close connection
      connection ! Close

      //this is some black magic that will be explained later
      //but the idea it's that it manages the socket status
      context become {
        case data: ByteString =>
          connection ! Write(data)
        case CommandFailed(w: Write) =>
          // O/S buffer was full
          log.warning("write failed")
        case Received(data) =>
        case "close" =>
          connection ! Close
        case _: ConnectionClosed =>
          log.warning("connection closed")
          //kills the agent
          context stop self
      }

    //if the agent receives a message that it doesn't understand, it sends a warning through the logger
    case x @ _ => log.warning("Something else is up. ---> " + x.toString)

  }
}
```
Ok, we have the agent, but we need to test if it's working as we tought. First of all, we have to lanch the agent from somewhere in our code. In this case I'm gonna invoke the agent from a Main scala object.

```scala

import actors.SocketProducer
import akka.actor.ActorSystem


import scala.collection.mutable

object Main {

  def main(args: Array[String]): Unit = {

    //Creates and Actor system in wich will reside/live our actor
    val actorSystem = ActorSystem.create("MyActorSystem")

    //data that we want to send with our actor
    val list = mutable.MutableList("1","2","Three","0100").map(_+"\n")
    //I append a \n to each element in the list for visibility

    //creates and runs the actor
    val actor = actorSystem.actorOf(SocketProducer.props("localhost",9000,list))


  }

}

```

That code is enough to launch our akka agent, but we need a somebody listen in `localhost:9000` to check if everything is alright. Before creating the consumer agent we can check it using the **netcat** terminal utiity/program in a terminal as follows:

```bash
  nc -lk 9000
```
So if we launch our scala code once **netcat** is running, **netcat** should print the following output:

```
1
2
Three
0100
```

Everything is working! nice!

## Building the Consumer

```scala
import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.io.{IO, Tcp}
import akka.util.ByteString

import scala.collection.mutable

object SocketConsumer {

  //Needed to create an Agent with a given configuration
  def props(port: Int): Props = Props(new SocketConsumer(port))

}

class SocketConsumer(port: Int) extends Actor with ActorLogging{

  import SocketConsumer._

  import akka.io.Tcp._
  import context.system

  // agent that manages the low level communication with the socket
  // this agent can send messages to out SocketProducer Agent, because
  // a reference to the SocketProducer agent is implicit passed
  // when we invoke IO(Tcp)
  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", port))

  // function that allows us to do some logic before the Agent is up
  override def preStart(): Unit = {
    log.info("Starting socket CONSUMER actor in port {}",port)
  }

  // function that allows us to do some logic once the agent is down
  override def postStop(): Unit = {
    log.info("Stopped socket CONSUMER actor")
  }

  override def receive = {

    case b @ Bound(localAddress) =>
      log.info("CONSUMER bound to: "+b)

    case CommandFailed(_: Bind) => context stop self

    case c @ Connected(remote, local) =>
      val connection = sender()
      connection ! Register(self)

    case Received(message) => log.info("Received: \n"+ message.decodeString("UTF8"))
    case PeerClosed     => context stop self

    //if the agent receives a message that it doesn't understand, it sends a warning through the logger
    case x @ _ => log.warning("Something else is up. ---> " + x.toString)

  }
}
```

Now we have to add the consumer to our Main scala object:

```scala
import actors.{SocketConsumer, SocketProducer}
import akka.actor.ActorSystem

import scala.collection.mutable

object Main {

  def main(args: Array[String]): Unit = {

    //Creates and Actor system in wich will reside/live our actor
    val actorSystem = ActorSystem.create("MyActorSystem")

    //data that we want to send with our actor
    val list = mutable.MutableList("1","2","Three","0100").map(_+"\n")

    //creates and runs the actor
    val actorConsumer = actorSystem.actorOf(SocketConsumer.props(9000))
    val actorProducer = actorSystem.actorOf(SocketProducer.props("localhost",9000,list))

  }

}
```
Finally if whe launch the program, the output should be:

```
[INFO] [09/05/2017 13:31:06.639] [MyActorSystem-akka.actor.default-dispatcher-3] [akka://MyActorSystem/user/$a] Starting socket CONSUMER actor in port 9000
[INFO] [09/05/2017 13:31:06.639] [MyActorSystem-akka.actor.default-dispatcher-2] [akka://MyActorSystem/user/$b] Starting socket PRODUCER actor with following config localhost:9000
[INFO] [09/05/2017 13:31:06.652] [MyActorSystem-akka.actor.default-dispatcher-2] [akka://MyActorSystem/user/$a] CONSUMER bound to: Bound(/127.0.0.1:9000)
[INFO] [09/05/2017 13:31:06.657] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$b] Trying to send message: 1

[INFO] [09/05/2017 13:31:06.666] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$b] Trying to send message: 2

[INFO] [09/05/2017 13:31:06.666] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$b] Trying to send message: Three

[INFO] [09/05/2017 13:31:06.666] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$b] Trying to send message: 0100

[INFO] [09/05/2017 13:31:06.669] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$a] Received:
1
2
Three
0100

[INFO] [09/05/2017 13:31:06.672] [MyActorSystem-akka.actor.default-dispatcher-7] [akka://MyActorSystem/user/$a] Stopped socket CONSUMER actor
[WARN] [09/05/2017 13:31:06.673] [MyActorSystem-akka.actor.default-dispatcher-2] [akka://MyActorSystem/user/$b] connection closed
[INFO] [09/05/2017 13:31:06.673] [MyActorSystem-akka.actor.default-dispatcher-2] [akka://MyActorSystem/user/$b] Stopped socket PRODUCER actor
```
