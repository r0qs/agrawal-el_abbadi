package agrawal

import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory

object Main {
	val debugConf = ConfigFactory.parseString("""
					akka {
						loglevel = DEBUG
						log-dead-letters = 10
						log-dead-letters-during-shutdown = on
						actor {
							debug {
								receive = on
								autoreceive = on
							}
						}
					}
					""" )
    def main(args: Array[String]) {
		val system = ActorSystem("Main", ConfigFactory.load(debugConf))
		val ac = system.actorOf(Props[AkkaMain])
		system.shutdown()
    }
}
