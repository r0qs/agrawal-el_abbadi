#!/bin/bash
# curl -ks https://gist.github.com/nicerobot/7851299/raw/run.sh | bash -
#[ -f State.scala ] || {
#[ -d 7851299 ] || {
#git clone https://gist.github.com/7851299.git
#}
#cd 7851299
#}
../../../../../../scala/Functional_Programming_Principles_Scala/sbt/bin/sbt -Dakka.loglevel=DEBUG -Dakka.actor.debug.receive=on '~run-main akka.Main agrawal.AkkaMain'
