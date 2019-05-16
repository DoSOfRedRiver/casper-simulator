package io.casperlabs.sim.simulation_framework

/**
  * Base class of messages that can be queued in the main events queue.
  */
sealed trait SimEventsQueueItem extends Ordered[SimEventsQueueItem] {
  val id: Long
  val scheduledDeliveryTime: Timepoint

  override def compare(that: SimEventsQueueItem): Int = scheduledDeliveryTime compare that.scheduledDeliveryTime
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

  case class NewAgentCreation[R](
                                  id: Long,
                                  agentInstance: Agent[R],
                                  scheduledDeliveryTime: Timepoint) extends SimEventsQueueItem

  case class PrivateEvent(
                                id: Long,
                                affectedAgent: AgentRef,
                                scheduledDeliveryTime: Timepoint,
                                payload: Any) extends SimEventsQueueItem

}



