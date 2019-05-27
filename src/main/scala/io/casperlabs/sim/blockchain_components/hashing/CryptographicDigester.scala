package io.casperlabs.sim.blockchain_components.hashing

/**
  * Decorates MessageDigest from JDK with a bunch of additional methods.
  */
trait CryptographicDigester {
  def generateHash(): Hash
  def updateWith(string: String): Unit
  def updateWith(byte: Byte): Unit
  def updateWith(int: Int): Unit
  def updateWith(long: Long): Unit
  def updateWith(boolean: Boolean): Unit
  def updateWith(bytearray: Array[Byte]): Unit
  def updateWith(hash: Hash): Unit = this.updateWith(hash.bytes)
}
