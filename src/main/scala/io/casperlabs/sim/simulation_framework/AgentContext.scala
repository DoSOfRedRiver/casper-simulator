package io.casperlabs.sim.simulation_framework

/**
  * Engine features that are exposed to agents.
  */
trait AgentContext {
  def findAgent(label: String): Option[AgentRef]
}
