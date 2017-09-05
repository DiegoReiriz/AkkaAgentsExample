package actors

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
