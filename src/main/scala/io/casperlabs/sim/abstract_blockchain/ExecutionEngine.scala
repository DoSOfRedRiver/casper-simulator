package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine._

trait ExecutionEngine[MS] {

  /**
    * Executes a transaction against given global state, producing a new global state.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transaction transaction to be executed
    * @return a pair (resulting global state, execution status)
    */
  def executeTransaction(gs: GlobalState[MS], transaction: Transaction, gasPrice: Ether, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult)

}
