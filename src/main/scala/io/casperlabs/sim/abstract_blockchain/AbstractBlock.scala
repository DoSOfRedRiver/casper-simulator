package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.Transaction
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

/**
  * Defines common properties of blocks across diverse blockchain implementations.
  */
trait AbstractBlock {
  def id: Hash
  def parents: IndexedSeq[Block]
  def justifications: IndexedSeq[Block]
  def transactions: IndexedSeq[Transaction]
}
