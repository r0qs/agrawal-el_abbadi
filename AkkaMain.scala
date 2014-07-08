package agrawal

import akka.actor.{ Actor, ActorRef, Props }
import akka.event.LoggingReceive
import scala.io._
import scala.util.Random
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import Process._
import Quorum._

object AkkaMain {
	case object KnockSomeone
}

class AkkaMain extends Actor {
	import AkkaMain._

	val resource = context.actorOf(Props[Resource], "SharedResource")
	var crashed : Set[Int] = Set.empty[Int]

	def gen_simple_binary_tree(n: Int, t: Vector[ActorRef]): Vector[ActorRef] =
		if (n == 1) Vector() :+ context.actorOf(Process.props(n-1, resource), s"p${n-1}")
		else gen_simple_binary_tree(n-1, t) :+ context.actorOf(Process.props(n-1, resource), s"p${n-1}")

	def get_out() {
		var i = Random.nextInt(tree.length/2)
		while (crashed contains i)
			i = Random.nextInt(tree.length/2)
		crashed += i
		println(crashed)
		tree.foreach(_ ! Crashed(i, tree(i)))

	}

	// A complete tree
	var tree : Vector[ActorRef] = gen_simple_binary_tree(7, tree)

	println(tree)
	tree.foreach(_ ! QuorumTree(tree))
	
	context.system.scheduler.scheduleOnce(5.seconds, self, KnockSomeone)
	context.system.scheduler.scheduleOnce(15.seconds, self, KnockSomeone)
	context.system.scheduler.scheduleOnce(30.seconds, self, KnockSomeone)

	def receive = LoggingReceive {
		case Done => println("Done Received from: " + sender) 
		case KnockSomeone => get_out()
		case _ => println("Eita: " + sender)
	}
}
