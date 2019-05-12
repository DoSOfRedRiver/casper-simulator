package io.casperlabs.sim.simulation_framework

/**
  * Base class of messages that can be queued in the main events queue.
  */
sealed trait SimEventsQueueItem[Msg] {
  val id: Long
  val scheduledTime: Timepoint
}

object SimEventsQueueItem {

  case class AgentToAgentMsg[Msg](
                                   id: Long,
                                   source: AgentId,
                                   destination: AgentId,
                                   sentTime: Timepoint,
                                   scheduledTime: Timepoint,
                                   payload: Msg) extends SimEventsQueueItem[Msg]

  case class ExternalEvent[Msg](
                                 id: Long,
                                 affectedAgent: AgentId,
                                 scheduledTime: Timepoint,
                                 payload: Msg) extends SimEventsQueueItem[Msg]

  case class NewAgentCreation[Msg](
                id: Long,
                agentInstance: Agent[Msg],
                scheduledTime: Timepoint) extends SimEventsQueueItem[Msg]

  case class PrivateEvent[Msg](
                                id: Long,
                                affectedAgent: AgentId,
                                scheduledTime: Timepoint,
                                payload: Msg) extends SimEventsQueueItem[Msg]
}

class QueueItemsOrdering[Msg] extends Ordering[SimEventsQueueItem[Msg]] {
  override def compare(x: SimEventsQueueItem[Msg], y: SimEventsQueueItem[Msg]): Int = x.scheduledTime compare y.scheduledTime
}


