package io.casperlabs.sim.simulation_framework

/**
  * General API of the simulation.
  * We are simulating a network of agents communicating via message-passing and exposed to a stream of external events.
  * The simulation runs in virtualised time
  */
trait Simulation[MsgPayload, ExtEventPayload] {
  def registerAgent(agent: Agent[MsgPayload, ExtEventPayload]): Unit
  def start(): Unit
}
