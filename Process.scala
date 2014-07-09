package agrawal

import java.io.{ FileWriter, PrintWriter }
import scala.io.Source
import scala.collection.immutable.Map
import scala.collection.breakOut
import scala.collection.mutable.{ Queue, Set }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.actor.{ Actor, ActorRef, Props, Status, ActorSystem }
import akka.event.LoggingReceive
import Quorum._

object Process {
	def props(id: Int, resource: ActorRef): Props = Props(new Process(id, resource))
	// Messages
	case class Crashed(id: Int, crash: ActorRef)
	case class Request(sid: Int)
	case class Permission(sid: Int)
	case object Released
	case object WantUseResource
	case object Abort
	case object Sleep
	case object WakeUp
	case object Update
	case object Done
}

class Process(pid: Int, resource: ActorRef) extends Actor {
	import Process._
	private val waitTime = 3.seconds
	private var tree : Vector[ActorRef] = Vector()
  	private var permissions : Set[ActorRef] =  Set.empty[ActorRef]
  	private var crashed : Set[ActorRef] = Set.empty[ActorRef]
	private val pending = Queue.empty[(Int, ActorRef)]
  	private val requests = Queue.empty[(Int, ActorRef)]
	private var procMap : Map[ActorRef, Int] = Map()
	//private var crashed = Set[ActorRef]()
	//private val requests = Queue[Map(Int, ActorRef)]

	def dice = Random.nextBoolean
	def getNextLetter(s: String) : Char = ('a' + ((s(0) -'a' + 1) % 26)).toChar
	
	def sendRequest() {
		if (requests.nonEmpty) {
			val (id, to) = requests.head
			if (!(crashed contains to))
				to ! Request(pid)
			else
				headRequestCrashed()
		}
	}

	def headRequestCrashed() {
		val (id, to) = requests.dequeue
		if (!isLeafOf(id, tree)) {
			val lid = left(id)
			val rid = right(id)
			if (lid < tree.length) {
				requests.enqueue(lid -> tree(lid))
			}
			if (rid < tree.length) {
				requests.enqueue(rid -> tree(rid))
			}
			sendRequest()
		}
		else {
			println("Leaf reached")
			//start new attempt to enter in the critical section and abort the actual
			permissions.clear()
			startIn(waitTime)
		}
	}

	def sendPermission() {
		if (pending.nonEmpty) {
			val (id, to) = pending.head
			if (!(crashed contains to))
				to ! Permission(pid)
			else {
				pending.dequeue
				sendPermission()
			}
		}
	}

	def stop(): Unit = {
		context.parent ! Done
		context.stop(self)
	}

	private def startIn(duration: FiniteDuration): Unit = {
		context.become(active)
		context.system.scheduler.scheduleOnce(duration*(Random.nextInt(5)+1), self, WantUseResource)
	}
 
 	def receive = passive

  	def passive : Receive = LoggingReceive {
		case QuorumTree(t) => 
			tree = t
			procMap = tree.map(e => (e, tree.indexOf(e)))(breakOut).toMap
			sender ! Done
			println("Process "+ pid + " start")
			println("Crashed of: " +pid+ " -> " + crashed)
			startIn(waitTime)
	}
	
	def critical : Receive = LoggingReceive {
		case Resource.Done =>
			// Exit critical section
			permissions.foreach(_ ! Released)
			permissions.clear()
			println("SAI: " + pid)
			startIn(waitTime)
		case Resource.Failed =>
			// send fail msg to parent
			context.stop(self)
	}

	def active : Receive = LoggingReceive {
  		case Request(fromId) =>
	  		pending.enqueue(fromId -> sender)
			val (id, to) = pending.head
     		if (to == sender) {
				sendPermission()
			}
		case Permission(fromId) =>
			permissions += sender
			val (id, from) = requests.dequeue
			if (!isLeafOf(id, tree)){
				val lid = left(id)
				val rid = right(id)
				if (lid < tree.length && rid < tree.length) {
					// Try to get permission from internodes (left or right)
					// TODO: use futures and timeout
					if(dice) {
						requests.enqueue(lid -> tree(lid))
					} else {
						requests.enqueue(rid -> tree(rid))
					}
				}
				else if (lid < tree.length) {
						requests.enqueue(lid -> tree(lid))
				} else if(rid < tree.length) {
						requests.enqueue(rid -> tree(rid))
				}
			}
			if (requests.nonEmpty) {
				sendRequest()
			}
			else {
				// Enter in critical section
				println("ENTREI: "+ pid)
				resource ! Resource.Add(pid, self, permissions)
				//context.become(critical)

				val source = Source.fromFile("shared.txt")
				var append : Boolean = true
				var c = 'a'
//				val regex = """([a-z])\s(\d)\s[-](\s\d)+""".r
				if(source.hasNext) {
//					val lastLine = source.getLines.toList.last.split(" ") match { case Array(x, y) => (x, y.toInt)}
					val lastLine = source.getLines.toList.last.lines.map(_.split(" ")).map(split => (split(0), split(1).toInt, split.slice(3,split.size).takeWhile(_ != '\n'))).toList.view
					c = getNextLetter(lastLine(0)._1)
					if (c == 'a') append = false
				}
				source.close()

				val dest = new PrintWriter(new FileWriter("shared.txt", append))
				dest.print(c.toString + " " + pid + " - ")
				permissions.foreach(p => dest.print(procMap(p) + " "))
				dest.println()
				dest.close()
				
				permissions.foreach(_ ! Released)
				permissions.clear()
				println("SAI: " + pid)
				startIn(waitTime)
			}
		case Released =>
			pending.dequeue
			sendPermission()
		case Crashed(id, crash) =>
			if (pid == id)
				context.become(passive)
			crashed += crash
			if (requests.nonEmpty) {
				val (rid, ractor) = requests.head
				if (ractor == crash)
					headRequestCrashed()
			}
			if (pending.nonEmpty) {
				val (hid, hactor) = pending.head
				if (hactor == crash) {
					pending.dequeue
					sendPermission()
				}
			}
		case WantUseResource =>
			//append root to requests
			println("Process " + pid + " want to enter in critical section")
			requests.enqueue(0 -> tree(0))
			sendRequest()
		case Abort	=> 
			stop()
		case Sleep =>
			context.become(passive)
		case _ : Status.Failure =>
			stop()
	}
}
