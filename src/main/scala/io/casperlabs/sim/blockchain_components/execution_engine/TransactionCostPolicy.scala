package io.casperlabs.sim.blockchain_components.execution_engine

trait TransactionCostPolicy {
  def calculateCost[MemoryState](tx: Transaction, ms: MemoryState): Gas
}
