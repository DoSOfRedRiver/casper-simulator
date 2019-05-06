package io.casperlabs.sim.blockchain_components.hashing

/**
  * Class used for fake hash values.
  */
case class HashValue(bits: Long) extends AnyVal {
  override def toString: String = bits.toHexString
}
