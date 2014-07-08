package agrawal

import akka.actor.ActorRef

object Quorum {
	def left (idx: Int)  : Int = 2 * idx + 1
	def right (idx: Int) : Int = 2 * idx + 2
	def parent (idx: Int): Int = (idx - 1) / 2
	def isLeafOf (idx: Int, tree: Vector[ActorRef]): Boolean = if ( left(idx) > tree.length && right(idx) > tree.length) true else false
}

case class QuorumTree(tree: Vector[ActorRef])
