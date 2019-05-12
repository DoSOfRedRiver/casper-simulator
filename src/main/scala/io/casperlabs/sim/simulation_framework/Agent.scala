package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  *
  * @tparam Msg base class of (simulation model specific) message payloads this agent class can deal with
  */
trait Agent[Msg] {

  /**
    * My reference (given by the engine).
    */
  def ref: AgentId

  /**
    * User-readable identifier of an agent - something like "client-324" or "server-15" or "bridge-1".
    * In any user-friendly presentation of the arena, names are going to be the primary way of "labelling" agents.
    * This name must be unique on the arena and must never change !
    * Agents naming schema is not decided here - we expect this to be heavily model-specific.
    */
  def label: String

  /**
    * Called by the engine only once - when this agent starts his life.
    */
  def onStartup(time: Timepoint): MsgHandlingResult[Msg]

  /**
    * Handler of incoming agent-to-agent messages.
    */
  def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Msg]): MsgHandlingResult[Msg]

  /**
    * Handler of incoming external events.
    */
  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Msg]): MsgHandlingResult[Msg]

  /**
    * Handler of incoming private events (= alerts I set for myself)
    */
  def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[Msg]): MsgHandlingResult[Msg]

}

object Agent {

  case class MsgHandlingResult[Msg](outgoingMessages: List[OutgoingMsgEnvelope[Msg]]) {

    def send(destination: AgentId, consumedTime: TimeDelta, payload: Msg): MsgHandlingResult[Msg] =
      MsgHandlingResult(OutgoingMsgEnvelope(destination, consumedTime, payload) :: outgoingMessages)

  }

  case class OutgoingMsgEnvelope[Msg](destination: AgentId, consumedTime: TimeDelta, payload: Msg)

  object MsgHandlingResult {
    def empty[Msg]: MsgHandlingResult[Msg] = MsgHandlingResult(Nil)
  }

//  def noOp[MsgPayload, ExtEventPayload, PrivatePayload](id: AgentRef): Agent[MsgPayload, ExtEventPayload, PrivatePayload] =
//    NoOp(id)
//
//  case class NoOp[MsgPayload, ExtEventPayload, PrivatePayload](override val ref: AgentRef) extends Agent[MsgPayload, ExtEventPayload, PrivatePayload] {
//    override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty
//
//    override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty
//
//    override def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[MsgPayload,ExtEventPayload, PrivatePayload]): MsgHandlingResult[MsgPayload, PrivatePayload] = MsgHandlingResult.empty
//
//    override def onStartup(): Unit = ()
//  }

}
