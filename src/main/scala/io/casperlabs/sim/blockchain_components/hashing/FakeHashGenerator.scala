package io.casperlabs.sim.blockchain_components.hashing

import scala.util.Random

/**
  * Utility class for generating fake hash values.
  * Blockchains are generally full of hashes here and there, and we want the simulated blockchain to give the feeling of a real one.
  */
object FakeHashGenerator {
  private val random = new Random()
  def nextHash(): HashValue = HashValue(random.nextLong())
}
