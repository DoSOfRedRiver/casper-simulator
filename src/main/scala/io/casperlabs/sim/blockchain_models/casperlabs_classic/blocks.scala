package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.Transaction
import io.casperlabs.sim.blockchain_components.hashing.{FakeHashGenerator, HashValue}
import io.casperlabs.sim.simulation_framework.AgentId

sealed abstract class Block {
  def id: HashValue
  def creator: AgentId // TODO: create specific Validator ID type?
  def dagLevel: Int
  def parents: IndexedSeq[Block]
  def justifications: IndexedSeq[Block]
  def transactions: IndexedSeq[Transaction]
}

case object Genesis extends Block {
  override val id: HashValue = FakeHashGenerator.nextHash()
  override val creator: AgentId = -1 // no one created genesis
  override def dagLevel: Int = 0
  override def parents: IndexedSeq[Block] = IndexedSeq.empty
  override def justifications: IndexedSeq[Block] = IndexedSeq.empty
  override def transactions: IndexedSeq[Transaction] = IndexedSeq.empty
}

case class NormalBlock(
                  id: HashValue,
                  creator: AgentId,
                  dagLevel: Int,
                  parents: IndexedSeq[Block],
                  justifications: IndexedSeq[Block],
                  transactions: IndexedSeq[Transaction]
          ) extends Block
{

}
