package io.casperlabs.sim.tmp

import scala.collection.immutable.{Map, Set}

object Estimator {
  //for sorting block scores
  implicit val decreasingOrder = Ordering[Long].reverse

  /**
    * When the BlockDag has an empty latestMessages, tips will return IndexedSeq(genesis)
    *
    * TODO: If the base block between the main parent and a secondary parent are more than
    * X blocks deep from the main parent, ignore. Additionally, the last finalized block must
    * be deeper than X blocks from the tip. This allows different validators to have
    * different last finalized blocks and still come up with the same estimator tips for a block.
    */
  def tips(blockDag: BlockDagRepresentation with Blockstore, lastFinalizedBlockHash: BlockHash): IndexedSeq[BlockMessage] = {

    def sortChildren(blocks: List[BlockHash], blockDag: BlockDagRepresentation, scores: Map[BlockHash, Long]): List[BlockHash] = {
      val unsortedNewBlocks = blocks.flatMap(replaceBlockHashWithChildren(_, blockDag, scores))
      val newBlocks = unsortedNewBlocks.distinct
      return if (blocks == newBlocks) blocks else sortChildren(newBlocks, blockDag, scores)
    }

    /**
      * Only include children that have been scored,
      * this ensures that the search does not go beyond
      * the messages defined by blockDag.latestMessages
      */
    def replaceBlockHashWithChildren(b: BlockHash, blockDag: BlockDagRepresentation, scores: Map[BlockHash, Long]): List[BlockHash] = {
      val c: Set[BlockHash] = blockDag.children(b).getOrElse(Set.empty[BlockHash]).filter(scores.contains)
      val nonEmptySeq: IndexedSeq[BlockHash] = if (c.nonEmpty) c.toIndexedSeq else IndexedSeq(b)
      val sorted: IndexedSeq[BlockHash] = nonEmptySeq.sortBy(blockhash => (scores(blockhash), blockhash))
      return sorted.toList
    }

    val latestMessagesHashes = blockDag.latestMessageHashes
    val scoresMap: Map[BlockHash, Long] = buildScoresMap(blockDag, latestMessagesHashes, lastFinalizedBlockHash)
    val sortedChildrenHash: List[BlockHash] = sortChildren(List(lastFinalizedBlockHash), blockDag, scoresMap)
    val maybeSortedChildren: List[Option[BlockMessage]] = sortedChildrenHash.map(blockDag.get)
    return maybeSortedChildren.flatten.toVector
  }

  def weightFromValidatorByDag(dag: BlockDagRepresentation, blockHash: BlockHash, validator: Validator): Long = {
    val blockMetadata: Option[BlockMetadata] = dag.lookup(blockHash)
    val primaryParent: Option[BlockHash] = blockMetadata.get.parents.headOption
    primaryParent match {
      case Some(firstParentHash) => dag.lookup(firstParentHash).get.weightMap.getOrElse(validator, 0L)
      case None => dag.lookup(blockHash).get.weightMap.getOrElse(validator, 0L)
    }
  }

  def buildScoresMap(blockDag: BlockDagRepresentation, latestMessagesHashes: Map[Validator, BlockHash], lastFinalizedBlockHash: BlockHash): Map[BlockHash, Long] = {

    def findParentsOfABlock(hashOfABlock: BlockHash, lastFinalizedBlockNumber: Long): List[BlockHash] =
      blockDag.lookup(hashOfABlock) match {
        case None => List.empty[BlockHash]
        case Some(block) =>
          if (block.blockNum < lastFinalizedBlockNumber)
            List.empty[BlockHash]
          else
            block.parents
      }

    def addValidatorWeightDownSupportingChain(scoreMap: Map[BlockHash, Long], validator: Validator, latestBlockHash: BlockHash): Map[BlockHash, Long] = {
      val lastFinalizedBlockNum = blockDag.lookup(lastFinalizedBlockHash).get.blockNum
      val reachableBlocksStream: Stream[BlockHash] = DagOperations.bfTraverseF[BlockHash](List(latestBlockHash))(findParentsOfABlock(_, lastFinalizedBlockNum))
      return reachableBlocksStream.foldLeft(scoreMap) {
        case (acc, hash) =>
          val currScore = acc.getOrElse(hash, 0L)
          val validatorWeight = weightFromValidatorByDag(blockDag, hash, validator)
          acc.updated(hash, currScore + validatorWeight)
        }

    }

    /**
      * Add scores to the blocks implicitly supported through
      * including a latest block as a "step parent"
      *
      * TODO: Add test where this matters
      */
    def addValidatorWeightToImplicitlySupported(blockDag: BlockDagRepresentation, scoreMap: Map[BlockHash, Long], validator: Validator, latestBlockHash: BlockHash): Map[BlockHash, Long] =
      blockDag.children(latestBlockHash) match {
        case None => scoreMap
        case Some(childrenSet) =>
          val childBlocksContainedInScoreMap: List[BlockHash] = childrenSet.filter(scoreMap.contains).toList
          childBlocksContainedInScoreMap.foldLeft(scoreMap) { case (acc, childHash) =>
            blockDag.lookup(childHash) match {
              case Some(childBlockMetaData) if childBlockMetaData.parents.size > 1 && ! childBlockMetaData.sender.sameElements(validator) =>
                val currScore = acc.getOrElse(childHash, 0L)
                val validatorWeight = childBlockMetaData.weightMap.getOrElse(validator, 0L)
                acc.updated(childHash, currScore + validatorWeight)
              case None => acc
            }
          }
      }

    latestMessagesHashes.toList.foldLeft(Map.empty[BlockHash, Long]) { case (acc, (validator: Validator, latestBlockHash: BlockHash)) =>
      val postValidatorWeightScoreMap: Map[BlockHash, Long] =  addValidatorWeightDownSupportingChain(acc, validator, latestBlockHash)
      addValidatorWeightToImplicitlySupported(blockDag, postValidatorWeightScoreMap, validator, latestBlockHash)
    }
  }
}
