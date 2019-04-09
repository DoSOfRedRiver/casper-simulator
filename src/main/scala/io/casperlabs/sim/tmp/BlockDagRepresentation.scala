package io.casperlabs.sim.tmp

trait BlockDagRepresentation {
  def children(blockHash: BlockHash): Option[Set[BlockHash]]
  def lookup(blockHash: BlockHash): Option[BlockMetadata]
  def latestMessageHashes: Map[Validator, BlockHash]


//  def contains(blockHash: BlockHash): Boolean
//  def topoSort(startBlockNumber: Long): Vector[Vector[BlockHash]]
//  def topoSortTail(tailLength: Int): Vector[Vector[BlockHash]]
//  def deriveOrdering(startBlockNumber: Long): Ordering[BlockMetadata]
//  def latestMessageHash(validator: Validator): Option[BlockHash]
//  def latestMessage(validator: Validator): Option[BlockMetadata]
//  def latestMessages: Map[Validator, BlockMetadata]

}
