package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult

/**
  * Represents a participant of the simulated network.
  *
  * @param context simulation engine API as seen by the agent
  */
abstract class Agent[MsgPayload, ExtEventPayload](context: SimulationContext) {

  def id: AgentId

  def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[MsgPayload,ExtEventPayload]): MsgHandlingResult[MsgPayload]

  def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[MsgPayload,ExtEventPayload]): MsgHandlingResult[MsgPayload]

  def startup(): Unit
}

object Agent {

  case class MsgHandlingResult[MsgPayload](outgoingMessages: Iterable[(AgentId, MsgPayload)], consumedTime: TimeDelta)
}
