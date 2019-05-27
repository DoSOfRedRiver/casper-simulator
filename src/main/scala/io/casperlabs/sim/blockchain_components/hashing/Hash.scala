package io.casperlabs.sim.blockchain_components.hashing

/**
  * Wrapper for a cryptographic hash value.
  */
case class Hash(bytes: Array[Byte]) extends Ordered[Hash] {
  override lazy val hashCode: Int = calculateHashCode //we memoize hashCode of this hash for performance optimization; it impacts all of the crucial data structures in the simulator

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

  private def calculateHashCode: Int = {
    var acc: Int = 0
    for (i <- 0 until bytes.length / 2) {
      val offset = i * 2
      val intValue = (bytes(offset)<<8 & 0xFF00) | (bytes(offset+1) & 0xFF)
      acc = acc ^ intValue
    }
    return acc
  }

  override def canEqual(that: Any): Boolean = that.isInstanceOf[Hash]

  override def equals(obj: Any): Boolean = {
    if (obj == null)
      false
    else
      this.canEqual(obj) && {
        val that = obj.asInstanceOf[Hash]
        hashCode == that.hashCode && bytes.sameElements(obj.asInstanceOf[Hash].bytes)
      }
  }

}

object Hash {
  implicit val ordering = new Ordering[Hash] {
    override def compare(x: Hash, y: Hash): Int = x.compare(y)
  }

}
