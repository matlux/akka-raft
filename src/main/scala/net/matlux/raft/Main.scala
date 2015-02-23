package net.matlux.raft

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Cancellable
import scala.concurrent.duration._
import scala.util.Random
import akka.actor.ActorRef
import akka.dispatch.Foreach

trait State

case class Leader() extends State
case class Follower() extends State
case class Candidate() extends State

trait Message

case class Start(peers: List[ActorRef]) extends Message
case class Tick() extends Message
case class ImLeader(term: Int) extends Message
case class RequestVote(term: Int) extends Message
case class Vote(name: ActorRef, term: Int) extends Message

/**
 * Actor implementing the leader election part of Raft
 */
class ServerActor extends Actor {
  import context.dispatcher
  val rg = new Random(self.hashCode())
  var state: State = Follower()
  var currentTerm: Int = 0
  var votedFor: Option[ActorRef] = None
  var timer: Cancellable = null
  var peers: List[ActorRef] = null
  var votedForMe: Set[ActorRef] = Set()
  val minTermMillis = 3000
  val rangeTermMillis = 5000
  val heartbeatPeriod = 1000

  def termPeriod = rg.nextInt(rangeTermMillis) + minTermMillis

  def debug(msg:String = "") = {
    val voted = votedFor match {
      case Some(actor) => if (actor == self) "self" else "someone else"
      case None => "nobody"
    }
    println(s"$state ${self.path.name} (voted for $voted, ${votedForMe.size} votes in term $currentTerm) $msg")
  }

  /**
   * Timer local to each actor, that is reset whenever we hear from the leader.
   * We start an election for a new term whenever this runs out.
   */
  def scheduleNextTerm = {
    if(timer != null) timer.cancel()
    timer = context.system.scheduler.scheduleOnce(termPeriod milliseconds, self, Tick())
  }

  /**
   * Periodically broadcast a message from the leader to let followers know
   * it's still alive.
   */
  def heartbeat {
    debug("heartbeat")
    if(timer != null) timer.cancel()
    timer = context.system.scheduler.scheduleOnce(heartbeatPeriod milliseconds, self, Tick())
    peers.foreach(p => p ! ImLeader(currentTerm))
  }

  /**
   * Respond to Raft messages
   */
  def receive = {
    case Start(p) => {
      debug("started")
      peers = p.filter(node => node != self)
      scheduleNextTerm
    }
    case Tick() => {
      state match {
        case Leader() => {
          heartbeat
        }
        case _ => {
          becomeCandidate
        }
      }
    }
    case RequestVote(term) => {
      if (term > currentTerm) {
        // We just began an election, or the last vote timed out.
        // During this election, it is permissible to accept messages from
        // the leader of the current term, but we fall into anarchy instead
        becomeFollower(term, None)
        votedFor = Some(sender)
        sender ! Vote(sender, term)
        debug(s"voted for ${sender.path.name}")
      } else {
        debug(s"already voted in term $term")
      }
    }
    case Vote(choice, term) => {
        if (choice == self && term == currentTerm && state == Candidate()) {
          votedForMe += sender
          if (hasMajority) becomeLeader
        } else {
          debug(s"ignoring vote of ${choice.path.name} for term $term")
        }
    }
    case ImLeader(term: Int) => {
      if(term >= currentTerm) {
        becomeFollower(term, Some(sender))
      } else {
        debug("ignoring heartbeat from previous term")
      }
    }
    case _ => println(self.path.name + "says huh?")
  }

  /**
   * @return Whether the actor has a majority of votes in the current term
   */
  def hasMajority: Boolean = {
    val total = peers.size + 1
    votedForMe.size >= total / 2.0
  }

  /**
   * Become the leader for this term.
   * The leader can now replicate state out to the followers.
   */
  def becomeLeader {
    debug("becoming leader")
    state = Leader()
    heartbeat
  }

  /**
   * Enter into the election for the next term's leader.
   */
  def becomeCandidate {
    debug("becoming candidate")
    currentTerm += 1
    votedFor = Some(self)
    votedForMe = Set(self)
    state = Candidate()
    peers.foreach(p => p ! RequestVote(currentTerm))
    scheduleNextTerm
  }

  /**
   * Immediately go back to the follower state.
   * If not already decided, a new leader should emerge after the current election.
   * @param term the new term
   * @param leader the new leader, if there is one
   */
  def becomeFollower(term: Int, leader: Option[ActorRef]) {
    debug(s"becoming follower of $leader for term $term")
    currentTerm = term
    votedFor = None
    votedForMe = Set()
    state = Follower()
    scheduleNextTerm
  }
}

object Main extends App {
  val system = ActorSystem("HelloSystem")
  val listOfPeers = List("s1", "s2", "s3", "s4", "s5")
  val listOfActors = listOfPeers.map(name => system.actorOf(Props[ServerActor], name = name))
  listOfActors.foreach(a => a ! Start(listOfActors))
}
