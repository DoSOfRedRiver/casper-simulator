package io.casperlabs.sim.blockchain_components.pretty_printing

import io.casperlabs.sim.blockchain_components.graphs.DoublyLinkedDag
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

import scala.collection.mutable

object BlockdagPrettyPrinter {
  val indentAtom: String = "  "

  def print(dag: DoublyLinkedDag[Block]): String = {
    val genesis = findGenesisBelow(dag, dag.tips.head)
    val canvas = new StringBuilder(1000)
    var vertexCounter: Int = 0
    val visited: mutable.Map[Block, Int] = new mutable.HashMap[Block, Int]

    def printSubtree(vertex: Block, level: Int): Unit = {
      canvas.append(indentAtom * level)
      visited.get(vertex) match {
        case Some(n) =>
          canvas.append(s"[->$n]")
          canvas.append("\n")
        case None =>
          vertexCounter += 1
          visited += vertex -> vertexCounter
          canvas.append(s"[$vertexCounter]")
          canvas.append(vertex.shortId)
          canvas.append("\n")
          for (child <- dag.sources(vertex))
            printSubtree(child, level+1)
      }
    }

    printSubtree(genesis, 0)
    return canvas.toString()
  }

  private def findGenesisBelow(dag: DoublyLinkedDag[Block], vertex: Block): Block = {
    val targets = dag.targets(vertex)
    if (targets.isEmpty)
      vertex
    else
      findGenesisBelow(dag, targets.head)
  }

}
