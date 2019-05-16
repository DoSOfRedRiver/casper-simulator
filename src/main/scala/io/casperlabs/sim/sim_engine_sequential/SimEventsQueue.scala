package io.casperlabs.sim.sim_engine_sequential

import io.casperlabs.sim.simulation_framework._
import io.casperlabs.sim.simulation_framework.SimEventsQueueItem._

import scala.collection.mutable

/**
  * A variation on priority queue that stands in the center of the simulator.
  *
  * Three types of events are enqueued (and prioritized by their timepoints):
  * type 1. agent-to-agent message delivery (by time of scheduled message delivery)
  * type 2. new agent showing up in the network (by time of creation) - provided by agents creation stream
  * type 3. external events - provided by external events stream (by time of event)
  * type 4: private events - these are events an agent sends to itself, which is an approach to implement "timers" feature
  *
  * Every event is handled by a single agent. In effect of this handling - arbitrary number of type-1 messages
  * is be created and so they must be explicitly enqueued. Type-2 and type-3 are created "automagically" in the background
  * as the simulation time is passing.
  *
  * @tparam MsgPayload
  * @tparam ExtEventPayload
  */
class SimEventsQueue[R] {
  private implicit val queueItemsOrdering: Ordering[SimEventsQueueItem] = new QueueItemsOrdering
  private val queue = new mutable.PriorityQueue[SimEventsQueueItem]
  private var numberOfQueuedAgentMessages: Int = 0
  private var extEvents: Option[ExternalEventsStream] = None
  private var createEvents: Option[AgentsCreationStream[R]] = None
  private var latestAgentMsgTimepoint: Timepoint = Timepoint(0)
  private var latestExtEventTimepoint: Timepoint = Timepoint(0)
  private var latestAgentCreationTimepoint: Timepoint = Timepoint(0)
  private var currentTime: Timepoint = Timepoint(0)

  def plugInExternalEventsStream(externalEventsGenerator: ExternalEventsStream): Unit ={
    extEvents = Some(externalEventsGenerator)
    ensureExtEventsAreGeneratedUpTo(latestAgentMsgTimepoint)
  }

  def plugInAgentsCreationStream(agentsCreationStream: AgentsCreationStream[R]): Unit = {
    createEvents = Some(agentsCreationStream)
    ensureAgentCreationsAreGeneratedUpTo(latestAgentMsgTimepoint)
  }

  def enqueue(msg: SimEventsQueueItem): Unit = {
    if (msg.scheduledDeliveryTime < currentTime)
      throw new RuntimeException(s"time order violation at sim events queue: current time was $currentTime, trying to add $msg")
    queue.enqueue(msg)
    numberOfQueuedAgentMessages += 1
    if (msg.scheduledDeliveryTime > latestAgentMsgTimepoint) {
      latestAgentMsgTimepoint = msg.scheduledDeliveryTime
      ensureExtEventsAreGeneratedUpTo(latestAgentMsgTimepoint)
      ensureAgentCreationsAreGeneratedUpTo(latestAgentMsgTimepoint)
    }
  }

  def dequeue(): Option[SimEventsQueueItem] = {
    if (numberOfQueuedAgentMessages == 0) {
      extEvents.foreach { externalEventsGenerator =>
        generateNextExtEvent(externalEventsGenerator)
      }
      createEvents.foreach { agentsCreationStream =>
        generateNextAgentCreationEvent(agentsCreationStream)
      }
    }

    if (queue.isEmpty)
      return None
    else {
      val nextEvent = queue.dequeue()
      nextEvent match {
        case e: AgentToAgentMsg => numberOfQueuedAgentMessages -= 1
        case other => //do nothing
      }

      currentTime = nextEvent.scheduledDeliveryTime
      return Some(nextEvent)
    }
  }

  private def ensureExtEventsAreGeneratedUpTo(timepoint: Timepoint): Unit =
    extEvents.foreach { externalEventsGenerator =>
      while (latestExtEventTimepoint < timepoint && externalEventsGenerator.hasNext)
        generateNextExtEvent(externalEventsGenerator)
    }

  private def ensureAgentCreationsAreGeneratedUpTo(timepoint: Timepoint): Unit =
    createEvents.foreach { agentsCreationStream =>
      while (latestAgentCreationTimepoint < timepoint && agentsCreationStream.hasNext)
        generateNextAgentCreationEvent(agentsCreationStream)
    }

  private def generateNextExtEvent(externalEventsGenerator: ExternalEventsStream): Unit = {
    if (externalEventsGenerator.hasNext) {
      val event = externalEventsGenerator.next()
      queue.enqueue(event)
      latestExtEventTimepoint = event.scheduledDeliveryTime
    }
  }

  private def generateNextAgentCreationEvent(agentsCreationStream: AgentsCreationStream[R]): Unit = {
    if (agentsCreationStream.hasNext) {
      val event = agentsCreationStream.next()
      queue.enqueue(event)
      latestAgentCreationTimepoint = event.scheduledDeliveryTime
    }
  }

}
