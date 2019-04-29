package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.AbstractBlock
import io.casperlabs.sim.blockchain_components.execution_engine.{BlockId, Gas, Transaction, ValidatorId}
import io.casperlabs.sim.blockchain_components.hashing.{FakeHashGenerator, HashValue}
import io.casperlabs.sim.simulation_framework.AgentId

sealed abstract class Block extends AbstractBlock {
  def dagLevel: Int
  def pTime: Gas //total amount of gas burned in past cone of this block (not including transactions in THIS block !), i.e. this is the value of block-time that transactions in this block can see
  def gasBurned: Gas //amount of gas burned in this block
}

case object Genesis extends Block {
  override val id: BlockId = FakeHashGenerator.nextHash()
  override val creator: ValidatorId = -1 // no one created genesis
  override def dagLevel: Int = 0
  override def parents: IndexedSeq[Block] = IndexedSeq.empty
  override def justifications: IndexedSeq[Block] = IndexedSeq.empty
  override def transactions: IndexedSeq[Transaction] = IndexedSeq.empty
  override def pTime = 0
  override def gasBurned: Gas = 0
}

case class NormalBlock(
                  id: BlockId,
                  creator: ValidatorId,
                  dagLevel: Int,
                  parents: IndexedSeq[Block],
                  justifications: IndexedSeq[Block],
                  transactions: IndexedSeq[Transaction],
                  pTime: Gas,
                  gasBurned: Gas
          ) extends Block
{

}
