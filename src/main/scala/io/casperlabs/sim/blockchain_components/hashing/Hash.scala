package io.casperlabs.sim.blockchain_components.hashing

/**
  * Wrapper for a cryptographic hash value.
  */
case class Hash(bytes: Array[Byte]) extends Ordered[Hash] {

  override def toString: String = convertBytesToHex(bytes)

  private def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

  override def compare(that: Hash): Int = {
    for (i <- 0 until math.min(bytes.length, that.bytes.length)) {
      val diff = bytes(i) - that.bytes(i)
      if (diff != 0)
        return diff
    }
    return 0
  }

}

object Hash {
  implicit val ordering = new Ordering[Hash] {
    override def compare(x: Hash, y: Hash): Int = x.compare(y)
  }

}
