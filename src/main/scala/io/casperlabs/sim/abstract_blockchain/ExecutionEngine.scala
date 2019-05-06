package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.Sha256Hash

/**
  * Abstraction of transaction processors.
  *
  * @tparam MS type of memory states
  * @tparam T type of transactions
  */
trait ExecutionEngine[MS, T] {

  /**
    * Executes a transaction against given global state, producing a new global state.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transaction transaction to be executed
    * @return a pair (resulting global state, execution status)
    */
  def executeTransaction(gs: GlobalState[MS], transaction: T, gasPrice: Ether, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult)

  /**
    * Calculates Sha256 hash of a given global state.
    * (This may be fake or real, depending on the implementation)
    */
  def globalStateHash(gs: GlobalState[MS]): Sha256Hash

}
