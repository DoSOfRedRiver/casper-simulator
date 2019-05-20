package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.abstract_blockchain.AbstractBlock.PseudoId
import io.casperlabs.sim.blockchain_components.execution_engine.Transaction
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

/**
  * Defines common properties of blocks across diverse blockchain implementations.
  */
trait AbstractBlock {
  def id: Hash
  def pseudoId: PseudoId
  def parents: IndexedSeq[Block]
  def justifications: IndexedSeq[Block]
  def transactions: IndexedSeq[Transaction]
}

object AbstractBlock {

  /**
    * This works as a secondary way of identifying blocks (where primary is block hash).
    * Useful because it is known in advance (= before a block is created) which allows to break some self-referential situations in global states.
    * Caution: due to equivocations, this pseudo-id may not be unique among given blockchain. Nevertheless, it is always unique within
    * any p-past-cone.
    */
  case class PseudoId(validator: ValidatorId, positionInPerValidatorChain: Int)
}
