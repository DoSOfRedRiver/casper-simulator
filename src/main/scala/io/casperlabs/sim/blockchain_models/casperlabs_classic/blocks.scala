package io.casperlabs.sim.blockchain_models.casperlabs_classic

import java.io.ByteArrayInputStream

import io.casperlabs.sim.abstract_blockchain.{AbstractBlock, AbstractNormalBlock, BlockId, ValidatorId}
import io.casperlabs.sim.blockchain_components.execution_engine.{Ether, Gas, Transaction, TransactionExecutionResult}
import io.casperlabs.sim.blockchain_components.hashing.{Hash, RealSha256Digester}

sealed abstract class Block extends AbstractBlock {
  def dagLevel: Int
  def pTime: Gas //total amount of gas burned in past cone of this block (not including transactions in THIS block !), i.e. this is the value of block-time that transactions in this block can see
  def gasBurned: Gas //amount of gas burned in this block
  def postStateHash: Hash
  def creator: ValidatorId //todo: should not be here; added for now so to make DoublyLinkedDag working (refactoring needed !)
  def weightsMap: Map[ValidatorId, Ether] //weights map used for transactions in this block (= after the initial bonding/unbonding queue processing is done !)

  //we override here default equals implementation for case classes
  override def equals(obj: Any): Boolean =
    obj match {
      case b: Block => this.id == b.id
      case _ => false
    }

  override def hashCode(): Int = id.hashCode

}

/**
  * Represents genesis blocks.
  *
  * Semantics of Genesis block is different than any other block:
  * 1. Genesis has no parent.
  * 2. Genesis has no creator (because it is given as a bootstrap).
  * 3. Genesis has no justifications.
  * 4. Genesis has no transactions.
  */
case class Genesis private (id: BlockId, weightsMap: Map[ValidatorId, Ether], postStateHash: Hash) extends Block {
  override def dagLevel: Int = 0
  override def parents: IndexedSeq[Block] = IndexedSeq.empty
  override def justifications: IndexedSeq[Block] = IndexedSeq.empty
  override def transactions: IndexedSeq[Transaction] = IndexedSeq.empty
  override def pTime = 0
  override def gasBurned: Gas = 0
  override def creator: ValidatorId = Block.psuedoValidatorIdUsedForGenesisBlock //todo: refactoring around DAG processing is needed to remove the need for fake validator id in this place
  override val pseudoId: AbstractBlock.PseudoId = AbstractBlock.PseudoId(Block.psuedoValidatorIdUsedForGenesisBlock, 0)
}

/**
  * Represents "normal" (= non-genesis) blocks.
  */
case class NormalBlock(
                        id: BlockId,
                        creator: ValidatorId,
                        dagLevel: Int,
                        positionInPerValidatorChain: Int,
                        parents: IndexedSeq[Block],
                        justifications: IndexedSeq[Block],
                        transactions: IndexedSeq[Transaction],
                        executionResults: IndexedSeq[TransactionExecutionResult],
                        pTime: Gas,
                        gasBurned: Gas,
                        weightsMap: Map[ValidatorId, Ether],
                        preStateHash: Hash,
                        postStateHash: Hash
                      ) extends Block with AbstractNormalBlock {

  override val pseudoId: AbstractBlock.PseudoId = AbstractBlock.PseudoId(creator, positionInPerValidatorChain)
}

object Block {

  def generateGenesisBlock(magicWord: String, weightsMap: Map[ValidatorId, Ether], postStateHash: Hash): Genesis = {
    val digester = new RealSha256Digester
    digester.updateWith(magicWord)
    val blockId = digester.generateHash()
    return Genesis(blockId, weightsMap, postStateHash)
  }

  val psuedoValidatorIdUsedForGenesisBlock: ValidatorId = -1

  val blankHash: Hash = Hash(new Array[Byte](32))

}

