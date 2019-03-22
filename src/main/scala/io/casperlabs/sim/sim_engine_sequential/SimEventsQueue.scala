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
  *
  * Every event is handled by a single agent. In effect of this handling - arbitrary number of type-1 messages
  * is be created and so they must be explicitly enqueued. Type-2 and type-3 are created "automagically" in the background
  * as the simulation time is passing.
  *
  * @param externalEventsGenerator
  * @tparam MsgPayload
  * @tparam ExtEventPayload
  */
class SimEventsQueue[MsgPayload, ExtEventPayload](externalEventsGenerator: ExternalEventsStream[MsgPayload, ExtEventPayload], agentsCreationStream: AgentsCreationStream[MsgPayload, ExtEventPayload]) {
  private implicit val queueItemsOrdering: Ordering[SimEventsQueueItem[MsgPayload, ExtEventPayload]] = new QueueItemsOrdering[MsgPayload,ExtEventPayload]
  private val queue = new mutable.PriorityQueue[SimEventsQueueItem[MsgPayload, ExtEventPayload]]
  private var numberOfQueuedAgentMessages: Int = 0
  private var latestAgentMsgTimepoint: Timepoint = Timepoint(0)
  private var latestExtEventTimepoint: Timepoint = Timepoint(0)
  private var latestAgentCreationTimepoint: Timepoint = Timepoint(0)

  def enqueue(msg: AgentToAgentMsg[MsgPayload, ExtEventPayload]): Unit = {
    queue.enqueue(msg)
    numberOfQueuedAgentMessages += 1
    if (msg.scheduledTime > latestAgentMsgTimepoint) {
      latestAgentMsgTimepoint = msg.scheduledTime
      ensureExtEventsAreGeneratedUpTo(latestAgentMsgTimepoint)
      ensureAgentCreationsAreGeneratedUpTo(latestAgentMsgTimepoint)
    }
  }

  def dequeue(): SimEventsQueueItem[MsgPayload, ExtEventPayload] = {
    if (numberOfQueuedAgentMessages == 0) {
      generateNextExtEvent()
      generateNextAgentCreationEvent()
    }

    val result = queue.dequeue()
    result match {
      case e: AgentToAgentMsg[_,_] => numberOfQueuedAgentMessages -= 1
      case other => //do nothing
    }

    return result
  }

  private def ensureExtEventsAreGeneratedUpTo(timepoint: Timepoint): Unit = {
    while (latestExtEventTimepoint < timepoint)
      generateNextExtEvent()
  }

  private def ensureAgentCreationsAreGeneratedUpTo(timepoint: Timepoint): Unit = {
    while (latestAgentCreationTimepoint < timepoint)
      generateNextAgentCreationEvent()
  }

  private def generateNextExtEvent(): Unit = {
    val event = externalEventsGenerator.next()
    queue.enqueue(event)
    latestExtEventTimepoint = event.scheduledTime
  }

  private def generateNextAgentCreationEvent(): Unit = {
    val event = agentsCreationStream.next()
    queue.enqueue(event)
    latestExtEventTimepoint = event.scheduledTime
  }


}
