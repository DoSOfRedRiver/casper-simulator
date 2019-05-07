package io.casperlabs.sim.blockchain_components.hashing

/**
  * Wrapper for a cryptographic hash value.
  */
case class Hash(bytes: Array[Byte]) {

  override def toString: String = convertBytesToHex(bytes)

  private def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }

}
