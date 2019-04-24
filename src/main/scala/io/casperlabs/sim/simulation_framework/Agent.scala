package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  *
  */
trait Agent[MsgPayload, ExtEventPayload, PrivatePayload] {

  def id: AgentId

  def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload]

  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload]

  def startup(): Unit
}

object Agent {

  case class MsgHandlingResult[MsgPayload](outgoingMessages: Iterable[(AgentId, MsgPayload)], consumedTime: TimeDelta)

  def noOp[MsgPayload, ExtEventPayload, PrivatePayload](id: AgentId): Agent[MsgPayload, ExtEventPayload, PrivatePayload] =
    NoOp(id)

  case class NoOp[MsgPayload, ExtEventPayload, PrivatePayload](override val id: AgentId) extends Agent[MsgPayload, ExtEventPayload, PrivatePayload] {
    override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload] = MsgHandlingResult(Nil, 0L)

    override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload] = MsgHandlingResult(Nil, 0L)

    override def startup(): Unit = ()
  }
}
