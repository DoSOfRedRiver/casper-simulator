package io.casperlabs.sim.simulation_framework

/**
  * API of the simulation.
  * We are simulating a network of agents communicating via message-passing and exposed to a stream of external events.
  * The simulation runs in virtualised time.
  */
trait Simulation[Msg] {

  def nextId(): Long

  def currentTime(): Timepoint

  def registerAgent(agent: Agent[Msg]): Unit

  def registerCommunication(event: SimEventsQueueItem.AgentToAgentMsg[Msg]): Unit

  def start(
             externalEventsGenerator: Iterator[SimEventsQueueItem.ExternalEvent[Msg]],
             agentsCreationStream: Iterator[SimEventsQueueItem.NewAgentCreation[Msg]]
           ): Unit
}
