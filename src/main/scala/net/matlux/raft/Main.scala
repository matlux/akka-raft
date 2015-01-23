package net.matlux.raft

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Cancellable
import scala.concurrent.duration._
import scala.util.Random
import akka.actor.ActorRef
import akka.dispatch.Foreach
//import akka.actor.context.dispatcher

class HelloActor extends Actor {
  def receive = {
    case "hello" => println("hello back at you")
    case _ => println("huh?")
  }
}

trait State

case class Leader extends State
case class Follower extends State
case class Candidate extends State

trait Message

case class Start(peers: List[ActorRef]) extends Message
case class Tick extends Message
case class ImLeader extends Message
case class RequestVote extends Message
case class Vote(name: ActorRef) extends Message

class ServerActor extends Actor {
  import context.dispatcher
  val rg = new Random(100)
  var state: State = Follower()
  var currentTerm: Int = 0
  var votedFor: Option[ActorRef] = None
  var timer: Cancellable = null
  var peers: List[ActorRef] = null
  var counter: Int = 0

  def receive = {
    case Start(p) => {
      println("started " + self.path.name)
      peers = p
      timer = context.system.scheduler.scheduleOnce(rg.nextInt(3000) milliseconds, self, Tick())
    }
    case Tick() => {
      println("tick " + self.path.name)
      votedFor = Some(self)
      state match {
        case Leader() => {
          
          peers.filter(p => p != self).foreach(p => p ! ImLeader())
          timer = context.system.scheduler.scheduleOnce(rg.nextInt(30) milliseconds, self, Tick())

        }

        case _ => {
          state = Candidate()
          //println(self.path.name + ", " + peers.filter(p => p != self).map(p => p.path.name))
          peers.filter(p => p!= self).foreach(p => p ! RequestVote())
          timer = context.system.scheduler.scheduleOnce(rg.nextInt(3000) milliseconds, self, Tick())

        }
      }
    }
    case RequestVote() => {
      println("RequestVote " + self.path.name + ",count " + counter)
      votedFor match {
        case Some(other) => sender ! Vote(other)
        case None => {
          votedFor = Some(sender)
          sender ! Vote(sender)
        }
      }
      timer = context.system.scheduler.scheduleOnce(rg.nextInt(30) milliseconds, self, Tick())
    }
    case Vote(actorRef) => {
            println("received vote " + self.path.name + ",count " + counter)
      if (actorRef == self) counter = counter + 1
      if (counter >= 3) {
        println("im leader" + self.path.name)
        state = Leader()
        peers.filter(p => p!= self).foreach(p => p ! ImLeader())
      }
    }
    case ImLeader() => {
      state = Follower()
      timer = context.system.scheduler.scheduleOnce(rg.nextInt(30) milliseconds, self, Tick())
    }
    case _ => println(self.path.name + "says hu?")

  }
}

object Main extends App {
  val system = ActorSystem("HelloSystem")

  val listOfPeers = List("s1", "s2", "s3", "s4", "s5")

  val listOfActors = listOfPeers.map(sname => system.actorOf(Props[ServerActor], name = sname))

  listOfActors.foreach(a => a ! Start(listOfActors))

  // default Actor constructor
  /*val helloActor = system.actorOf(Props[HelloActor], name = "helloactor")
  helloActor ! "hello"
  helloActor ! "buenos dias"*/

}

  

