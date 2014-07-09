package agrawal

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Set
import akka.actor.{ Actor, ActorRef }
import akka.event.LoggingReceive

object Resource {
	case class Add(id: Int, ref: ActorRef, quorum: Set[ActorRef])
	case object Get
	case object Done
	case object Failed
}

class Resource extends Actor {
	import Resource._
	val accessList : ListBuffer[Int] = new ListBuffer()

	def receive = LoggingReceive {
		case Add(id: Int, ref: ActorRef, quorum: Set[ActorRef]) =>	
					accessList += id
					println("Process " + id + " add to access list: " + accessList)
					println("Quorum: " + quorum)
					sender ! Done
		case Get => sender ! accessList
	}
}
