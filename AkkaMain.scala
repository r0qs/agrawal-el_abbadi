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
	case class Stop(id: Int)
}

class AkkaMain extends Actor {
	import AkkaMain._

	var crashed : Set[Int] = Set.empty[Int]

	def gen_simple_binary_tree(n: Int, t: Vector[ActorRef]): Vector[ActorRef] =
		if (n == 1) Vector() :+ context.actorOf(Process.props(n-1), s"p${n-1}")
		else gen_simple_binary_tree(n-1, t) :+ context.actorOf(Process.props(n-1), s"p${n-1}")

	def get_out(i: Int) {
//		var i = Random.nextInt(tree.length/2)
//		while (crashed contains i)
//			i = Random.nextInt(tree.length/2)
		crashed += i
		tree.foreach(_ ! Crashed(i, tree(i)))

	}

	// A complete tree
	var tree : Vector[ActorRef] = gen_simple_binary_tree(7, tree)
	tree.foreach(_ ! Start(tree))
	
	context.system.scheduler.scheduleOnce(15.seconds, self, Stop(1))
	context.system.scheduler.scheduleOnce(40.seconds, self, Stop(0))
	context.system.scheduler.scheduleOnce(60.seconds, self, Stop(2))

	def receive = LoggingReceive {
		case Stop(id) => get_out(id)
		case _ => println("Receive a unexpected message from: " + sender)
	}
}
