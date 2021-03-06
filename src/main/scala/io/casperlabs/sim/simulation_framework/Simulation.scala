package io.casperlabs.sim.simulation_framework

/**
  * API of the simulation.
  * We are simulating a network of agents communicating via message-passing and exposed to a stream of external events.
  * The simulation runs in virtualised time.
  */
trait Simulation[R] {

  /**
    * Registration of pre-existing agents.
    * Only works before the simulation is launched.
    */
  def preRegisterAgent(agent: Agent[R]): AgentRef

  def currentTime(): Timepoint

  def start(
             externalEventsGenerator: ExternalEventsStream,
             agentsCreationStream: AgentsCreationStream[R]
           ): Unit

}
