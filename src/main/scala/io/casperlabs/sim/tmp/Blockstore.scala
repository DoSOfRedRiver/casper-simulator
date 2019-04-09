package io.casperlabs.sim.tmp

trait Blockstore {
//  def put(blockMessage: BlockMessage): Unit = put((blockMessage.blockHash, blockMessage))
//  def put(blockHash: BlockHash, blockMessage: BlockMessage): Unit = put((blockHash, blockMessage))
  def get(blockHash: BlockHash): Option[BlockMessage]
//  def find(p: BlockHash => Boolean): Seq[(BlockHash, BlockMessage)]
//  def put(f: => (BlockHash, BlockMessage)): Unit
//  def apply(blockHash: BlockHash): BlockMessage = get(blockHash).get
//  def contains(blockHash: BlockHash): Boolean = get(blockHash).isDefined
//  def checkpoint(): Unit
//  def clear(): Unit
//  def close(): Unit
}
