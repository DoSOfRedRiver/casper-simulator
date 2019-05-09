package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.Hash

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

  /**
    * Calculates id of a block.
    * This is a hash of crucial data.
    *
    * Caution: we do not include post-state hash as part of the identity of the block. This makes the block-level logic easier to implement
    * (because of the way block rewards are implemented, block id is used in structures that are part of global state.
    * Covering post-state hash with block-hash calculation would cause recursive dependency ("hash quine").
    *
    * @param creator validator who proposed the block
    * @param parents identifiers of parent blocks
    * @param justifications identifiers of justification blocks
    * @param transactions transactions
    * @param preStateHash hash of global state, against which the validator was executing this block (= result of mering of parent blocks)
    * @return new block id
    */
  def calculateBlockId(creator: ValidatorId, parents: IndexedSeq[BlockId], justifications: IndexedSeq[BlockId], transactions: IndexedSeq[Transaction], preStateHash: Hash): BlockId

  /**
    * Generates id of genesis block.
    * Because of semantics of block ids, pretty much anything can be used as an input to the digester for making a genesis hash.
    *
    * @param magicWord "magic word" that will stay as the origin of given blockchain instance ("casperlabs" sounds like a good example here)
    * @return id of genesis block
    */
  def generateGenesisId(magicWord: String): BlockId

}
