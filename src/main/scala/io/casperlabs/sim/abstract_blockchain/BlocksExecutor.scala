package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

/**
  * Encapsulates block-level execution logic.
  *
  * @tparam MS type of memory states
  * @tparam B type of blocks
  */
trait BlocksExecutor[MS, B <: AbstractBlock] {

  /**
    * Executes transactions in a block (+ any block-level logic).
    * This method is dedicated for a block received from the network (i.e. the whole block is there).
    *
    * @param preState global state snapshot at the moment before the block is executed
    * @param block sequence of transactions
    * @return (post-state, gas burned in block)
    */
  def executeBlockAsVerifier(preState: GlobalState[MS], block: B): (GlobalState[MS], Gas)

  /**
    * Executes transactions in a block (+ any block-level logic).
    * This method is dedicated for the block proposing sequence (where the block is not created yet).
    *
    * @param preState
    * @param pTime
    * @param blockId
    * @param creator
    * @param parents
    * @param justifications
    * @param transactions
    * @return
    */
  def executeBlockAsCreator(preState: GlobalState[MS], pTime: Gas, blockId: BlockId, creator: ValidatorId, transactions: IndexedSeq[Transaction]): (GlobalState[MS], Gas)

  def calculateBlockId(creator: ValidatorId, parents: IndexedSeq[BlockId], justifications: IndexedSeq[BlockId], transactions: IndexedSeq[Transaction], preStateHash: Hash): BlockId

  def generateGenesisId(magicWord: String): BlockId

}
