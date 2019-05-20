package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.abstract_blockchain.BlocksExecutor.BlockCreationResult
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_models.casperlabs_classic.{Block, NormalBlock}

/**
  * Encapsulates block-level execution logic.
  *
  * @tparam MS type of memory states
  * @tparam B type of blocks
  */
trait BlocksExecutor[MS, B <: AbstractNormalBlock] {

  /**
    * Executes transactions in a block (+ any block-level logic).
    * This method is dedicated for a block received from the network (i.e. the whole block is there).
    *
    * @param preState global state snapshot at the moment before the block is executed
    * @param block sequence of transactions
    * @return (post-state, gas burned in block)
    */
  def executeBlock(preState: GlobalState[MS], block: B): (GlobalState[MS], Gas, Map[Transaction, TransactionExecutionResult])

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
  @deprecated
  def executeBlockAsCreator(preState: GlobalState[MS], pTime: Gas, blockId: BlockId, creator: ValidatorId, transactions: IndexedSeq[Transaction]): (GlobalState[MS], Gas) = ???

  def createBlock(
                   preState: GlobalState[MS],
                   pTime: Gas,
                   dagLevel: Int,
                   positionInPerValidatorChain: Int,
                   creator: ValidatorId,
                   parents: IndexedSeq[Block],
                   justifications: IndexedSeq[Block],
                   transactions: IndexedSeq[Transaction]): BlockCreationResult[MS]

  /**
    * Generates id of genesis block.
    * Because of semantics of block ids, pretty much anything can be used as an input to the digester for making a genesis hash.
    *
    * @param magicWord "magic word" that will stay as the origin of given blockchain instance ("casperlabs" sounds like a good example here)
    * @return id of genesis block
    */
  def generateGenesisId(magicWord: String): BlockId

}

object BlocksExecutor {

  case class BlockCreationResult[MS](block: NormalBlock, postState: GlobalState[MS], txExecutionResults: Map[Transaction, TransactionExecutionResult])

}
