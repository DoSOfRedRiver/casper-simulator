package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.DoublyLinkedDag
import io.casperlabs.sim.simulation_framework.AgentId

import scala.annotation.tailrec
import scala.collection.mutable

object BlockdagUtils {
  def lmdGhost(
                latestMessages: Map[AgentId, Block],
                weights: Map[AgentId, Int], // TODO: factor out and put in blocks
                pDag: DoublyLinkedDag[Block],
                genesis: Block
              ): IndexedSeq[Block] = {
    val scores: mutable.HashMap[Block, Int] = mutable.HashMap.empty

    // Scoring phase
    for(
      validator <- weights.keys;
      weight = weights(validator);
      block <- DoublyLinkedDag.targetTraverse(latestMessages.get(validator), pDag)
    ) {
      val score = scores.getOrElseUpdate(block, 0)
      scores.update(block, score + weight)
    }

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

}
