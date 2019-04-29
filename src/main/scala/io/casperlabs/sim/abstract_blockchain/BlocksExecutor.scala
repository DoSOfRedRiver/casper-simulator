package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.{Gas, GlobalState}
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

/**
  * Encapsulates block-level execution logic.
  *
  * @tparam MS type of memory states
  */
trait BlocksExecutor[MS] {

  /**
    * Executes a block.
    *
    * @param preState global state snapshot at the moment before the block is executed
    * @param block sequence of transactions
    * @return (post-state, gas burned in block)
    */
  def executeBlock(preState: GlobalState[MS], block: Block): (GlobalState[MS], Gas)

}
