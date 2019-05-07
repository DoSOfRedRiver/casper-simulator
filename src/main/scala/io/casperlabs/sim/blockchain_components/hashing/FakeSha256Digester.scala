package io.casperlabs.sim.blockchain_components.hashing

import scala.util.Random

class FakeSha256Digester(random: Random) extends CryptographicDigester {

  override def generateHash(): Hash = {
    val a = new Array[Byte](32)
    random.nextBytes(a)
    return Hash(a)
  }

  override def updateWith(string: String): Unit = {}

  override def updateWith(int: Int): Unit = {}

  override def updateWith(long: Long): Unit = {}

  override def updateWith(boolean: Boolean): Unit = {}

  override def updateWith(byte: Byte): Unit = {}

  override def updateWith(bytearray: Array[Byte]): Unit = {}
}
