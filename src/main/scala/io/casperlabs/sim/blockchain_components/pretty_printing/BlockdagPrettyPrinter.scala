package io.casperlabs.sim.blockchain_components.pretty_printing

import io.casperlabs.sim.abstract_blockchain.AbstractBlock
import io.casperlabs.sim.blockchain_components.graphs.DoublyLinkedDag

object BlockdagPrettyPrinter {

  def print(dag: DoublyLinkedDag[AbstractBlock]): String = {
    val genesis = findGenesis(dag)

//    val stringBuilder = new StringBuilder(1000)
//    for (block <- DoublyLinkedDag.sourceTraverse(List(genesis), dag))
//      if (block)
    ???
  }

  def findGenesis(dag: DoublyLinkedDag[AbstractBlock]): AbstractBlock = dag.tips.head

  def findGenesisBelow(dag: DoublyLinkedDag[AbstractBlock], vertex: AbstractBlock): AbstractBlock = {
    val targets = dag.targets(vertex)
    if (targets.isEmpty)
      vertex
    else
      findGenesisBelow(dag, targets.head)
  }

}
