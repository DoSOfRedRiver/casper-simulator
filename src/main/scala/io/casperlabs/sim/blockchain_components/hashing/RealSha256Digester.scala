package io.casperlabs.sim.blockchain_components.hashing

import java.security.MessageDigest

class RealSha256Digester extends CryptographicDigester {
  private val internalDigester: MessageDigest = MessageDigest.getInstance("SHA-256")

  def generateHash(): Hash = Hash(internalDigester.digest())

  def updateWith(string: String): Unit = internalDigester.update(string.getBytes("UTF-8"))

  def updateWith(byte: Byte): Unit = internalDigester.update(byte)

  def updateWith(data: Int): Unit = {
    val bytearray = Array[Byte](
      ((data >> 24) & 0xff).asInstanceOf[Byte],
      ((data >> 16) & 0xff).asInstanceOf[Byte],
      ((data >> 8) & 0xff).asInstanceOf[Byte],
      ((data >> 0) & 0xff).asInstanceOf[Byte]
    )
    internalDigester.update(bytearray)
  }

  def updateWith(data: Long): Unit = {
    val bytearray = Array[Byte](
      ((data >> 56) & 0xff).asInstanceOf[Byte],
      ((data >> 48) & 0xff).asInstanceOf[Byte],
      ((data >> 40) & 0xff).asInstanceOf[Byte],
      ((data >> 32) & 0xff).asInstanceOf[Byte],
      ((data >> 24) & 0xff).asInstanceOf[Byte],
      ((data >> 16) & 0xff).asInstanceOf[Byte],
      ((data >> 8) & 0xff).asInstanceOf[Byte],
      ((data >> 0) & 0xff).asInstanceOf[Byte]
    )
    internalDigester.update(bytearray)
  }

  def updateWith(boolean: Boolean): Unit = {
    if (boolean)
      internalDigester.update(1.toByte)
    else
      internalDigester.update(0.toByte)
  }

  def updateWith(bytearray: Array[Byte]): Unit = internalDigester.update(bytearray)
}
