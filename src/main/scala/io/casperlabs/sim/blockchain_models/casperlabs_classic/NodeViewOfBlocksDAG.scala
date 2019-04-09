package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.Transaction

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
  * Blocks DAG as seen by a node.
  */
class NodeViewOfBlocksDAG(node: Node) {

  val latestBlocks: Map[Node, Block] = new HashMap[Node, Block]
  private var blocksMissing: mutable.HashSet[Block] = new mutable.HashSet[Block]

  def membershipHeads: Iterable[Block] = ???

  def justificationHeads: Iterable[Block] = ???

  def latestBlockForNode(node: Node): Block = ???

  def ghostWinnerBlock: Block = ???

  def ghostTipsCollection: IndexedSeq[Block] = ???

  def selectParentsForNewBlockToBeCreated(candidateTransactions: Transaction): IndexedSeq[NormalBlock] = {
    //todo
    ???
  }

  def checkIfJustificationsArePresent(block: NormalBlock): Boolean = ???


}
