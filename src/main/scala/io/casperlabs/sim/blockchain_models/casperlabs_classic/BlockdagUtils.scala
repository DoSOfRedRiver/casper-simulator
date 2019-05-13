package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.Ether
import io.casperlabs.sim.blockchain_components.graphs.DoublyLinkedDag
import io.casperlabs.sim.simulation_framework.AgentRef

import scala.annotation.tailrec
import scala.collection.mutable

object BlockdagUtils {

  /** Compute the scores for LMD GHOST.
    * 
    * @tparam Id type of validator ids
    * @tparam Vertex type of blocks in the DAG
    * 
    * @param latestMessages Mapping of validators to their latest messages in the DAG
    * @param weights Function of (block, validator) to weight of that block. This function
    *                depends on the block as well because of validator rotation (i.e. 
    *                changes to the validator weights map as the DAG progresses).
    * @param pDag The DAG created bby block parent pointers
    * 
    * @return The scores map
    */
  def lmdScoring[Id, Vertex](
                   latestMessages: Map[Id, Vertex],
                   weights: (Vertex, Id) => Ether,
                   pDag: DoublyLinkedDag[Vertex],
                  ): mutable.HashMap[Vertex, Ether] = {
    val scores: mutable.HashMap[Vertex, Ether] = mutable.HashMap.empty

    // Scoring phase
    latestMessages.keys.foreach { validator =>
      DoublyLinkedDag.targetTraverse(latestMessages.get(validator), pDag).foreach { block =>
        val score = scores.getOrElseUpdate(block, 0)
        val weight = weights(block, validator);
        scores.update(block, score + weight)
      }
    }

    scores
  }

  def lmdGhost(
                latestMessages: Map[AgentRef, Block],
                weights: Map[AgentRef, Int], // TODO: factor out and put in blocks
                pDag: DoublyLinkedDag[Block],
                genesis: Block
              ): IndexedSeq[Block] = {
    val scores = lmdScoring(
      latestMessages, 
      (block: Block, validator: AgentRef) => weights(validator),
      pDag
    )

    // traversal phase
    @tailrec
    def loop(blocks: Vector[Block]): Vector[Block] = {
      val updated = blocks.flatMap(block => {
        val children = pDag
          .sources(block)
          .toIndexedSeq
          .sortBy(scores.apply)
        if (children.isEmpty) Vector(block)
        else children
      })

      if (updated == blocks) blocks
      else loop(updated)
    }
    loop(Vector(genesis))
  }


  def lmdMainchainGhost[Id, Vertex, Ord: Ordering](
                                                    latestMessages: Map[Id, Vertex],
                                                    validatorWeightExtractor: (Vertex, Id) => Ether,
                                                    pDag: DoublyLinkedDag[Vertex],
                                                    genesis: Vertex,
                                                    tieBreaker: Vertex => Ord
                          ): Vertex = {
    val mainChainSubDag = new DoublyLinkedDag[Vertex] {
      def targets(n: Vertex): Iterable[Vertex] = pDag.targets(n).take(1)
      def sources(n: Vertex): Iterable[Vertex] = pDag.sources(n)
      def contains(n: Vertex): Boolean = pDag.contains(n)
      def tips: Iterable[Vertex] = pDag.tips
      def insert(n: Vertex, targets: Iterable[Vertex]): DoublyLinkedDag.InsertResult[Vertex] = pDag.insert(n, targets)
    }
    val scores: mutable.HashMap[Vertex, Ether] = lmdScoring(latestMessages, validatorWeightExtractor, mainChainSubDag)

    // traversal phase
    @tailrec
    def loop(block: Vertex): Vertex = 
      pDag.sources(block).toList match {
        case Nil => block
        case nonempty => loop( nonempty.maxBy(b => scores.getOrElse(b, 0L) -> tieBreaker(b)) )
      }

    loop(genesis)
  }

}
