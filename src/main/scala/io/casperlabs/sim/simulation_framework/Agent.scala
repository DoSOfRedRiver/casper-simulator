package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  *
  */
trait Agent[MsgPayload, ExtEventPayload] {

  def id: AgentId

  def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload,ExtEventPayload]): MsgHandlingResult[MsgPayload]

  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload,ExtEventPayload]): MsgHandlingResult[MsgPayload]

  def startup(): Unit
}

object Agent {

  case class MsgHandlingResult[MsgPayload](outgoingMessages: Iterable[(AgentId, MsgPayload)], consumedTime: TimeDelta)

  def noOp[MsgPayload, ExtEventPayload](id: AgentId): Agent[MsgPayload, ExtEventPayload] =
    NoOp(id)

  case class NoOp[MsgPayload, ExtEventPayload](override val id: AgentId) extends Agent[MsgPayload, ExtEventPayload] {
    override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload, ExtEventPayload]): MsgHandlingResult[MsgPayload] = MsgHandlingResult(Nil, 0L)

    override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload]): MsgHandlingResult[MsgPayload] = MsgHandlingResult(Nil, 0L)

    override def startup(): Unit = ()
  }
}
