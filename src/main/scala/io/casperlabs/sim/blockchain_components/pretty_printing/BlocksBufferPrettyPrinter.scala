package io.casperlabs.sim.blockchain_components.pretty_printing

import io.casperlabs.sim.blockchain_components.graphs.IndexedTwoArgRelation
import io.casperlabs.sim.blockchain_models.casperlabs_classic.NormalBlock

object BlocksBufferPrettyPrinter {

  def print(buffer: IndexedTwoArgRelation[NormalBlock,NormalBlock]): String = {
    val canvas = new StringBuilder(buffer.size * 10)
    for (s <- buffer.sources) {
      canvas.append(s.shortId)
      canvas.append(" --> ")
      canvas.append("(")
      val targetBlocksCommaSeparatedList: String = buffer.findTargetsFor(s).map(b => b.shortId).mkString(",")
      canvas.append(targetBlocksCommaSeparatedList)
      canvas.append(")")
      canvas.append("\n")
    }
    return canvas.toString()
  }

}
