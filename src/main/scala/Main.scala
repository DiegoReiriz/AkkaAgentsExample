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
