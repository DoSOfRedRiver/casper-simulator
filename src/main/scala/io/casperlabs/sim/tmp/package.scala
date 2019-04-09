package io.casperlabs.sim

package object tmp {
  type BlockHash = Array[Byte]
  type Validator = Array[Byte]

  implicit val orderingForBlockHash = new Ordering[BlockHash] {

    override def compare(left: BlockHash, right: BlockHash): Int = {
      for (i <- 0 until math.min(left.length, right.length)) {
        val a: Byte = left(i)
        val b: Byte = right(i)
        if (a != b) return a - b
      }
      return 0
    }

  }

//  def sort1(a: List[BlockHash]): List[BlockHash] = a.sorted
//
//  def sort2(a: List[(Int, BlockHash)]): List[(Int, BlockHash)] = a.sorted

}
