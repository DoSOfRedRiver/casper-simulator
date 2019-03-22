package io.casperlabs.sim.blockchain_models.casperlabs

sealed abstract class Block {
  def id: BlockId
  def dagLevel: Int
  def parents: IndexedSeq[Block]
  def justifications: IndexedSeq[Block]
  def transactions: IndexedSeq[Transaction]
  def hash: Long
}

case object Genesis extends Block {
  override def id: BlockId = "genesis"
  override def dagLevel: Int = 0
  override def parents: IndexedSeq[Block] = IndexedSeq.empty
  override def justifications: IndexedSeq[Block] = IndexedSeq.empty
  override def transactions: IndexedSeq[Transaction] = IndexedSeq.empty
  override val hash: Long = 3141592653589793238L //easter egg
}

case class NormalBlock(
                  id: BlockId,
                  creator: Node,
                  dagLevel: Int,
                  parents: IndexedSeq[Block],
                  justifications: IndexedSeq[Block],
                  transactions: IndexedSeq[Transaction]
          ) extends Block
{

  private var memoizedHash: Option[Long] = None

  /**
    * Plays the role of block's hash, although for the needs of simulation we are faking the 'real' hash with something that is faster to calculate.
    */
  override def hash: Long = memoizedHash getOrElse {
    val h = creator.id * id.hashCode
    memoizedHash = Some(h)
    h
  }

}
