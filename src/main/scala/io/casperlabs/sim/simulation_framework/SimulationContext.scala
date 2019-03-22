package io.casperlabs.sim.simulation_framework

/**
  * Simulation engine API as seen by the agent.
  */
trait SimulationContext {
  def currentTime: Timepoint
  def sendMsg(sendingTime: Timepoint, destination: AgentId)
  def broadcastMsg()
  def audit(entry: HistoryEntry)
  def halt(msg: String)
}
