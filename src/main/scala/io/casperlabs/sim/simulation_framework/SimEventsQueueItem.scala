package io.casperlabs.sim.simulation_framework

/**
  * Base class of messages that can be queued in the main events queue.
  *
  * @tparam MsgPayload
  * @tparam ExtEventPayload
  * @tparam PrivatePayload
  */
sealed trait SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload] {
  val id: Long
  val scheduledTime: Timepoint
}

object SimEventsQueueItem {

  case class AgentToAgentMsg[MsgPayload,ExtEventPayload,PrivatePayload](
                id: Long,
                source: AgentId,
                destination: AgentId,
                sentTime: Timepoint,
                scheduledTime: Timepoint,
                payload: MsgPayload) extends SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload]

  case class ExternalEvent[MsgPayload,ExtEventPayload,PrivatePayload](
                id: Long,
                affectedAgent: AgentId,
                scheduledTime: Timepoint,
                payload: ExtEventPayload) extends SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload]

  case class NewAgentCreation[MsgPayload,ExtEventPayload,PrivatePayload](
                id: Long,
                agentInstance: Agent[MsgPayload,ExtEventPayload,PrivatePayload],
                scheduledTime: Timepoint) extends SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload]

}

class QueueItemsOrdering[MsgPayload,ExtEventPayload,PrivatePayload] extends Ordering[SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload]] {
  override def compare(x: SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload], y: SimEventsQueueItem[MsgPayload,ExtEventPayload,PrivatePayload]): Int = x.scheduledTime compare y.scheduledTime
}


