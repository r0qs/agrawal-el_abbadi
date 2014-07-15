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
	case class Start(tree: Vector[ActorRef])
	case object Released
	case object Restart
	case object WantUseResource
	case object Abort
	case object Sleep
	case object Update
}

class Process(pid: Int, resource: ActorRef) extends Actor {
	import Process._
	private var tree : Vector[ActorRef] = Vector()
  	private var permissions : Set[ActorRef] =  Set.empty[ActorRef]
  	private var crashed : Set[ActorRef] = Set.empty[ActorRef]
	private val pending = Queue.empty[(Int, ActorRef)]
  	private val requests = Queue.empty[(Int, ActorRef)]
	private var procMap : Map[ActorRef, Int] = Map()
	//private var crashed = Set[ActorRef]()
	//private val requests = Queue[Map(Int, ActorRef)]

	def dice = Random.nextBoolean
	def waitTime = (Random.nextInt(2)+1).seconds
	def getNextLetter(s: String) : Char = ('a' + ((s(0) -'a' + 1) % 26)).toChar
	def showQueue(q: Queue[(Int, ActorRef)]) = q.map(p => p._1)

	def sendRequest() {
		println(pid + " REQUEST " + showQueue(requests))
		val (id, to) = requests.head
		if (!(crashed contains to)) {
			println(pid + " send REQUEST to " + id)
			context.system.scheduler.scheduleOnce(waitTime, to, Request(pid))
			//to ! Request(pid)
		}
		else {
			println(pid + " HEAD REQUEST CRASH " + id)
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
			println(pid + " LEAF reached " + id)
			//start new attempt to enter in the critical section and abort the actual
			permissions.clear()
			startIn(waitTime)
		}
	}

	def sendPermission() {
		println(pid + " PENDING " + showQueue(pending))
		if (pending.nonEmpty) {
			val (id, to) = pending.head
			if (!(crashed contains to)) {
				println(pid + " send PERMISSION to " + id)
				context.system.scheduler.scheduleOnce(waitTime, to, Permission(pid))
				//to ! Permission(pid)
			}
			else {
				println(pid + " HEAD PERMISSION CRASH " + id)
				pending.dequeue
				sendPermission()
			}
		}
	}

	private def startIn(duration: FiniteDuration): Unit = {
		context.become(active)
		context.system.scheduler.scheduleOnce(duration, self, WantUseResource)
	}
 
 	def receive = passive

  	def passive : Receive = LoggingReceive {
		case Start(t) => 
			tree = t
			procMap = tree.map(e => (e, tree.indexOf(e)))(breakOut).toMap
			startIn(waitTime)
		case Restart =>
			crashed -= self
			tree.foreach(_ ! Update)
			startIn(waitTime)
	}
	
	def critical : Receive = LoggingReceive {
		case Resource.Done =>
			// Exit critical section
			permissions.foreach(_ ! Released)
			permissions.clear()
			startIn(waitTime)
		case Resource.Failed =>
			// TODO: send fail msg to parent
			//context.parent ! Done
			context.stop(self)
	}

	def active : Receive = LoggingReceive {
  		case Request(fromId) =>
			println(pid + " receive REQUEST from " + fromId)
			pending.enqueue(fromId -> sender)
			val (id, to) = pending.head
     		if (to == sender) {
				sendPermission()
			}
		case Permission(fromId) =>
			println(pid + " receive PERMISSION from " + fromId)
			if (!(crashed contains sender)) {
				permissions += sender
				val (id, from) = requests.dequeue
				if (!isLeafOf(id, tree)){
					val lid = left(id)
					val rid = right(id)
					if (lid < tree.length && rid < tree.length) {
						// Try to get permission from internodes (left or right)
						// TODO: use futures and timeout
						requests.enqueue(lid -> tree(lid))
					} else if (lid < tree.length) {
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
					resource ! Resource.Add(pid, self, permissions)

					val source = Source.fromFile("shared.txt")
					var append : Boolean = true
					var c = 'a'
//					val regex = """([a-z])\s(\d)\s[-](\s\d)+""".r
					if(source.hasNext) {
//						val lastLine = source.getLines.toList.last.split(" ") match { case Array(x, y) => (x, y.toInt)}
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
				
					//context.become(critical)
					//permissions.foreach(_ ! Released)
					for (to <- permissions) { context.system.scheduler.scheduleOnce(waitTime, to, Released)}
					permissions.clear()
					startIn(waitTime)
					// Exit critical section
				}
			}
		case Released =>
			println(pid + " receive RELEASED from " + procMap(sender))
			pending.dequeue
			sendPermission()
		case Crashed(id, crash) =>
			println(pid + " receive CRASHED from " + id)
			// FIXME: each process should detect failures by itself
			if (pid == id)
				context.stop(self)
//				context.become(passive)
			crashed += crash
			if (requests.nonEmpty) {
				println(pid + " CRASH REQUESTS: " + requests + " HEAD " + requests.head)
				val (rid, ractor) = requests.head
				if (ractor == crash)
					headRequestCrashed()
			}
			if (pending.nonEmpty) {
				println(pid + " CRASH PENDING: " + pending)
				val (hid, hactor) = pending.head
				if (hactor == crash) {
					pending.dequeue
					println(pid + " CRASH PENDING DEQUEUE: " + pending)
					sendPermission()
				}
			}
		case WantUseResource =>
			//append root to requests
			requests.enqueue(0 -> tree(0))
			sendRequest()
		case Update =>
			crashed -= sender
		case Abort	=> 
			context.stop(self)
		case Sleep =>
			context.become(passive)
		case _  =>
	}
}
