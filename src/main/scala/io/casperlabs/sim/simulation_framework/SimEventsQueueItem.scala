package io.casperlabs.sim.simulation_framework

sealed trait SimEventsQueueItem[MsgPayload,ExtEventPayload] {
  val id: Long
  val scheduledTime: Timepoint
}

object SimEventsQueueItem {

  case class AgentToAgentMsg[MsgPayload,ExtEventPayload](
                id: Long,
                source: AgentId,
                destination: AgentId,
                sentTime: Timepoint,
                scheduledTime: Timepoint,
                payload: MsgPayload) extends SimEventsQueueItem[MsgPayload,ExtEventPayload]

  case class ExternalEvent[MsgPayload,ExtEventPayload](
                id: Long,
                affectedAgent: AgentId,
                scheduledTime: Timepoint,
                payload: ExtEventPayload) extends SimEventsQueueItem[MsgPayload,ExtEventPayload]

  case class NewAgentCreation[MsgPayload,ExtEventPayload](
                id: Long,
                agentInstance: Agent[MsgPayload,ExtEventPayload],
                scheduledTime: Timepoint) extends SimEventsQueueItem[MsgPayload,ExtEventPayload]

}

class QueueItemsOrdering[MsgPayload,ExtEventPayload] extends Ordering[SimEventsQueueItem[MsgPayload,ExtEventPayload]] {
  override def compare(x: SimEventsQueueItem[MsgPayload,ExtEventPayload], y: SimEventsQueueItem[MsgPayload,ExtEventPayload]): Int = x.scheduledTime compare y.scheduledTime
}


