package io.casperlabs.sim.abstract_blockchain

/**
  * Common contract for non-genesis blocks.
  */
trait AbstractNormalBlock extends AbstractBlock {
  def creator: ValidatorId

}
