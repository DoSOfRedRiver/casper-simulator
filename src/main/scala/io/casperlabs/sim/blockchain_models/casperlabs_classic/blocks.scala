package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.AbstractBlock
import io.casperlabs.sim.blockchain_components.execution_engine.{BlockId, Gas, Transaction, ValidatorId}
import io.casperlabs.sim.blockchain_components.hashing.{Hash, RealSha256Digester}

sealed abstract class Block extends AbstractBlock {
  def dagLevel: Int
  def pTime: Gas //total amount of gas burned in past cone of this block (not including transactions in THIS block !), i.e. this is the value of block-time that transactions in this block can see
  def gasBurned: Gas //amount of gas burned in this block
  def postStateHash: Hash
}

case class Genesis private (id: BlockId, postStateHash: Hash) extends Block {
  override val creator: ValidatorId = -1 // no one created genesis
  override def dagLevel: Int = 0
  override def parents: IndexedSeq[Block] = IndexedSeq.empty
  override def justifications: IndexedSeq[Block] = IndexedSeq.empty
  override def transactions: IndexedSeq[Transaction] = IndexedSeq.empty
  override def pTime = 0
  override def gasBurned: Gas = 0

}

object Genesis {

  def generate(magicWord: String, postStateHash: Hash): Genesis = {
    val digester = new RealSha256Digester
    digester.updateWith(magicWord)
    val blockId = digester.generateHash()
    return Genesis(blockId, postStateHash)
  }

}

case class NormalBlock(
                  id: BlockId,
                  creator: ValidatorId,
                  dagLevel: Int,
                  parents: IndexedSeq[Block],
                  justifications: IndexedSeq[Block],
                  transactions: IndexedSeq[Transaction],
                  pTime: Gas,
                  gasBurned: Gas,
                  preStateHash: Hash,
                  postStateHash: Hash
          ) extends Block
{

}
