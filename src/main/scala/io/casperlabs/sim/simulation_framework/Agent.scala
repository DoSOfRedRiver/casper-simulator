package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  *
  */
trait Agent[MsgPayload, ExtEventPayload, PrivatePayload] {

  def id: AgentId

  def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload]

  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload]

  def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload]

  def startup(): Unit
}

object Agent {

  case class MsgHandlingResult[MsgPayload, PrivatePayload](
                                                            outgoingMessages: Iterable[(AgentId, MsgPayload)],
                                                            privateEvents: Iterable[(Timepoint, PrivatePayload)],
                                                            consumedTime: TimeDelta
                                                          )
  object MsgHandlingResult {
    def empty[MsgPayload, PrivatePayload]: MsgHandlingResult[MsgPayload, PrivatePayload] =
      MsgHandlingResult(Nil, Nil, 0L)
  }

  def noOp[MsgPayload, ExtEventPayload, PrivatePayload](id: AgentId): Agent[MsgPayload, ExtEventPayload, PrivatePayload] =
    NoOp(id)

  case class NoOp[MsgPayload, ExtEventPayload, PrivatePayload](override val id: AgentId) extends Agent[MsgPayload, ExtEventPayload, PrivatePayload] {
    override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty

    override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty

    override def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty

    override def startup(): Unit = ()
  }
}
