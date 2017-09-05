package actors

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
