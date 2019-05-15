package io.casperlabs.sim.blockchain_components.hashing

/**
  * Class used for fake hash values.
  */
@deprecated
case class FakeHash(bits: Long) extends AnyVal {
  override def toString: String = bits.toHexString
}
