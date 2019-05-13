package io.casperlabs.sim.simulation_framework

/**
  * Base class of messages that can be queued in the main events queue.
  */
sealed trait SimEventsQueueItem {
  val id: Long
  val scheduledDeliveryTime: Timepoint
}

object SimEventsQueueItem {

  case class AgentToAgentMsg(
                                   id: Long,
                                   source: AgentRef,
                                   destination: AgentRef,
                                   sentTime: Timepoint,
                                   scheduledDeliveryTime: Timepoint,
                                   payload: Any) extends SimEventsQueueItem

  case class ExternalEvent(
                                 id: Long,
                                 affectedAgent: AgentRef,
                                 scheduledDeliveryTime: Timepoint,
                                 payload: Any) extends SimEventsQueueItem

  case class NewAgentCreation(
                                    id: Long,
                                    agentInstanceCreator: AgentRef => Agent,
                                    scheduledDeliveryTime: Timepoint) extends SimEventsQueueItem

  case class PrivateEvent(
                                id: Long,
                                affectedAgent: AgentRef,
                                scheduledDeliveryTime: Timepoint,
                                payload: Any) extends SimEventsQueueItem
}

class QueueItemsOrdering extends Ordering[SimEventsQueueItem] {
  override def compare(x: SimEventsQueueItem, y: SimEventsQueueItem): Int = x.scheduledDeliveryTime compare y.scheduledDeliveryTime
}


