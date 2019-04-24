package io.casperlabs.sim.simulation_framework

/**
  * API of the simulation.
  * We are simulating a network of agents communicating via message-passing and exposed to a stream of external events.
  * The simulation runs in virtualised time.
  */
trait Simulation[MsgPayload, ExtEventPayload, PrivatePayload] {
  def nextId(): Long
  def currentTime(): Timepoint
  def registerAgent(agent: Agent[MsgPayload, ExtEventPayload, PrivatePayload]): Unit
  def registerCommunication(event: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload, PrivatePayload]): Unit
  def start(
             externalEventsGenerator: ExternalEventsStream[MsgPayload, ExtEventPayload, PrivatePayload],
             agentsCreationStream: AgentsCreationStream[MsgPayload, ExtEventPayload, PrivatePayload]
           ): Unit
}
