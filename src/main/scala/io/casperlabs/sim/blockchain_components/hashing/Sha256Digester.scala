package io.casperlabs.sim.blockchain_components.hashing

import java.security.MessageDigest

/**
  * Decorates MessageDigest from JDK with a bunch of additional methods (and the algorithm is fixed: SHA-256).
  */
class Sha256Digester {
  private val internalDigester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def generateHash(): Sha256Hash = Sha256Hash(internalDigester.digest())

  def updateWith(string: String): Unit = internalDigester.update(string.getBytes("UTF-8"))

  def updateWith(int: Int): Unit = internalDigester.update()

  def updateWith(long: Long)

  def updateWith(boolean: Boolean)

}
