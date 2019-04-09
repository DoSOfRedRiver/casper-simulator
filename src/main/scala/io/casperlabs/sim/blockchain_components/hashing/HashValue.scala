package io.casperlabs.sim.blockchain_components.hashing

import io.casperlabs.sim.blockchain_components.execution_engine.BlockId

/**
  * Class used for fake hash values.
  */
case class HashValue(bits: Long) {
  override def toString: BlockId = 1L.toHexString
}
