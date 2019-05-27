package io.casperlabs.sim.sim_engine_sequential

import io.casperlabs.sim.simulation_framework.AgentRef

case class AgentRefImpl(address: Int) extends AgentRef {
  override def toString: String = s"agent-$address"
}
