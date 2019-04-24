package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Trait representing a DAG which has O(1) lookup of both
  * targets of a node (i.e. arrows from `n` to other nodes) and
  * sources of a node (i.e. arrows from other nodes to `n`).
  *
  * @tparam Node The type of the nodes in the DAG.
  */
trait DoublyLinkedDag[Node] {
  def targets(n: Node): Iterable[Node]
  def sources(n: Node): Iterable[Node]

  def contains(n: Node): Boolean

  /**
    * List of nodes which are only sources, but not targets,
    * i.e. nodes with only outgoing arrows and no incoming arrows.
    * @return list of nodes which are only sources.
    */
  def tips: Iterable[Node]

  /**
    * Add a new node to the DAG. The targets of the new node
    * must be provided, but the sources will naturally be inferred
    * (since when a node is first added it has no sources and it
    * becomes a source of the nodes in its target list).
    * @param n New node to add
    * @param targets List of nodes arrows from `n` point to
    */
  def insert(n: Node, targets: Iterable[Node]): DoublyLinkedDag.InsertResult[Node]
}

object DoublyLinkedDag {
  sealed trait InsertResult[Node]
  object InsertResult {
    case class AlreadyInserted[Node]() extends InsertResult[Node]

    /**
      * Success case returns a continuation for actually
      * inserting the element (i.e. the insert is lazy and
      * only by running the continuation does the insert happen).
      * The purpose of this is to allow attempting an insert while
      * not modifying the data structure and not having to repeat any
      * checks if we do decide to go through with the insert.
      * @param continuation Function to do the actual insert.
      */
    case class Success[Node](continuation: () => Unit) extends InsertResult[Node]
    case class MissingTargets[Node](nodes: Iterable[Node]) extends InsertResult[Node]
  }

  /**
    * Breadth-first traversal
    * @param starts starting nodes (first nodes to visit)
    * @param nextNodes function determining which nodes a given nodes is connected to
    * @tparam Node type of nodes in the graph
    * @return an iterator traversing the nodes in breadth-first order
    */
  def bfTraverse[Node](
                        starts: Iterable[Node],
                        nextNodes: Node => Iterable[Node]
                      ): Iterator[Node] = new Iterator[Node] {
    private val q: mutable.Queue[Node] = mutable.Queue.empty
    private val visited: mutable.HashSet[Node] = mutable.HashSet.empty
    starts.foreach(q.enqueue(_))

    override def next(): Node = if (hasNext) {
      val node = q.dequeue()
      nextNodes(node).foreach(n => if (!visited(n)) q.enqueue(n))
      node
    } else {
      Iterator.empty.next()
    }

    @tailrec
    override def hasNext: Boolean = if (q.nonEmpty) {
      val n = q.head // take head to as to not remove
      if (visited(n)) {
        // Already visited n, discard and try again
        val _ = q.dequeue()
        hasNext
      } else {
        true
      }
    } else {
      false
    }
  }

  def targetTraverse[Node](
                            starts: Iterable[Node],
                            dag: DoublyLinkedDag[Node]
                          ): Iterator[Node] =
    bfTraverse(starts, dag.targets)

  def sourceTraverse[Node](
                            starts: Iterable[Node],
                            dag: DoublyLinkedDag[Node]
                          ): Iterator[Node] =
    bfTraverse(starts, dag.sources)

  def blockDag(
                genesis: Block,
                prev: Block => IndexedSeq[Block]
              ): DoublyLinkedDag[Block] = new DoublyLinkedDag[Block] {
    type BlockList = mutable.ArrayBuffer[Block]
    private def empty: BlockList = mutable.ArrayBuffer.empty[Block]
    private val childMap: mutable.HashMap[Block, BlockList] = mutable.HashMap.empty
    private val childless: mutable.HashSet[Block] = mutable.HashSet.empty
    private val allBlocks: mutable.HashSet[Block] = mutable.HashSet.empty

    override def contains(b: Block): Boolean = allBlocks.contains(b)

    override def targets(b: Block): Iterable[Block] =
      if (allBlocks.contains(b)) prev(b)
      else Nil

    override def sources(b: Block): Iterable[Block] = childMap.getOrElse(b, Nil)

    override def tips: Iterable[Block] = childless

    override def insert(b: Block, bTargets: Iterable[Block]): DoublyLinkedDag.InsertResult[Block] =
      // Do not add a block twice
      if (allBlocks.contains(b)) DoublyLinkedDag.InsertResult.AlreadyInserted()
      else {
        val missing = bTargets.filterNot(allBlocks.contains)
        if (missing.nonEmpty)
          DoublyLinkedDag.InsertResult.MissingTargets(missing)
        else
          DoublyLinkedDag.InsertResult.Success(() => {
            for (block <- bTargets) {
              childMap.getOrElseUpdate(block, empty).append(b)
              // targets of new block are no longer childless
              val _ = childless.remove(block)
            }
            // Blocks must be inserted in a topological order (see check above), so
            // when a block is inserted it is not a child of any existing block.
            childMap.update(b, empty)
            childless.add(b)
          })
      }
  }

  def pBlockDag(genesis: Block): DoublyLinkedDag[Block] =
    blockDag(genesis, _.parents)

  def jBlockDag(genesis: Block): DoublyLinkedDag[Block] =
    blockDag(genesis, _.justifications)
}
