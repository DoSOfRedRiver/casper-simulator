package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  */
trait Agent {

  /**
    * My reference (given by the engine).
    */
  def ref: AgentRef

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
  def onStartup(time: Timepoint): MsgHandlingResult

  /**
    * Handler of incoming agent-to-agent messages.
    */
  def handleMessage(msg: SimEventsQueueItem.AgentToAgentMsg): MsgHandlingResult

  /**
    * Handler of incoming external events.
    */
  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent): MsgHandlingResult

  /**
    * Handler of incoming private events (= alerts I set for myself)
    */
  def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent): MsgHandlingResult

}

object Agent {

  case class MsgHandlingResult(outgoingMessages: List[OutgoingMsgEnvelope])

  //encodes stuff that agents produce as a result of handling a message
  //it is the sim engine role to transform them to actual SimEventsQueue items
  sealed abstract class OutgoingMsgEnvelope {
    val relativeTimeOfSendingThisMessage: TimeDelta //relative against the timepoint or arrival of message, which was the currently handled message when the 'send' happened
    val payload: Any
  }

  object OutgoingMsgEnvelope {
    case class Tell(destination: AgentRef, relativeTimeOfSendingThisMessage: TimeDelta, payload: Any) extends OutgoingMsgEnvelope
    case class Private(relativeTimeOfSendingThisMessage: TimeDelta, deliveryDelay: TimeDelta, payload: Any) extends OutgoingMsgEnvelope
    //todo: in case of adding request-response support, both Request and Response will be added here (with correlation as part of the structure)
  }


  object MsgHandlingResult {
    def empty: MsgHandlingResult = MsgHandlingResult(Nil)
  }

}
