package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.{Transaction, ValidatorId}
import io.casperlabs.sim.blockchain_components.hashing.HashValue
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

/**
  * Defines common properties of blocks across diverse blockchain implementations.
  */
trait AbstractBlock {
  def id: HashValue
  def creator: ValidatorId
  def parents: IndexedSeq[Block]
  def justifications: IndexedSeq[Block]
  def transactions: IndexedSeq[Transaction]
}
