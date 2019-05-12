package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Trait representing a DAG which has O(1) lookup of both
  * targets of a node (i.e. arrows from `n` to other nodes) and
  * sources of a node (i.e. arrows from other nodes to `n`).
  *
  * @tparam Vertex The type of the nodes in the DAG.
  */
trait DoublyLinkedDag[Vertex] {
  def targets(n: Vertex): Iterable[Vertex]
  def sources(n: Vertex): Iterable[Vertex]

  def contains(n: Vertex): Boolean

  /**
    * List of nodes which are only sources, but not targets,
    * i.e. nodes with only outgoing arrows and no incoming arrows.
    * @return list of nodes which are only sources.
    */
  def tips: Iterable[Vertex]

  /**
    * Add a new node to the DAG. The targets of the new node
    * must be provided, but the sources will naturally be inferred
    * (since when a node is first added it has no sources and it
    * becomes a source of the nodes in its target list).
    * @param n New node to add
    * @param targets List of nodes arrows from `n` point to
    */
  def insert(n: Vertex, targets: Iterable[Vertex]): DoublyLinkedDag.InsertResult[Vertex]
}

object DoublyLinkedDag {
  sealed trait InsertResult[Vertex]
  object InsertResult {
    case class AlreadyInserted[Vertex]() extends InsertResult[Vertex]

    /**
      * Success case returns a continuation for actually
      * inserting the element (i.e. the insert is lazy and
      * only by running the continuation does the insert happen).
      * The purpose of this is to allow attempting an insert while
      * not modifying the data structure and not having to repeat any
      * checks if we do decide to go through with the insert.
      * @param continuation Function to do the actual insert.
      */
    case class Success[Vertex](continuation: () => Unit) extends InsertResult[Vertex]
    case class MissingTargets[Vertex](nodes: Iterable[Vertex]) extends InsertResult[Vertex]
  }

  /**
    * Breadth-first traversal
    * @param starts starting nodes (first nodes to visit)
    * @param nextVertices function determining which nodes a given nodes is connected to
    * @tparam Vertex type of nodes in the graph
    * @return an iterator traversing the nodes in breadth-first order
    */
  def bfTraverse[Vertex](
                        starts: Iterable[Vertex],
                        nextVertices: Vertex => Iterable[Vertex]
                      ): Iterator[Vertex] = new Iterator[Vertex] {
    private val q: mutable.Queue[Vertex] = mutable.Queue.empty
    private val visited: mutable.HashSet[Vertex] = mutable.HashSet.empty
    starts.foreach(q.enqueue(_))

    override def next(): Vertex = if (hasNext) {
      val node = q.dequeue()
      visited.add(node)
      nextVertices(node).foreach(n => if (!visited(n)) q.enqueue(n))
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

  def targetTraverse[Vertex](
                            starts: Iterable[Vertex],
                            dag: DoublyLinkedDag[Vertex]
                          ): Iterator[Vertex] =
    bfTraverse(starts, dag.targets)

  def sourceTraverse[Vertex](
                            starts: Iterable[Vertex],
                            dag: DoublyLinkedDag[Vertex]
                          ): Iterator[Vertex] =
    bfTraverse(starts, dag.sources)

  def prePointerDag[Vertex](
                genesis: Vertex,
                prev: Vertex => IndexedSeq[Vertex]
              ): DoublyLinkedDag[Vertex] = new DoublyLinkedDag[Vertex] {
    type VertexList = mutable.ArrayBuffer[Vertex]
    private def empty: VertexList = mutable.ArrayBuffer.empty[Vertex]
    private val childMap: mutable.HashMap[Vertex, VertexList] = mutable.HashMap.empty
    private val childless: mutable.HashSet[Vertex] = mutable.HashSet.empty
    private val allVertices: mutable.HashSet[Vertex] = mutable.HashSet.empty

    childless.add(genesis)
    allVertices.add(genesis)

    override def contains(b: Vertex): Boolean = allVertices.contains(b)

    override def targets(b: Vertex): Iterable[Vertex] =
      if (allVertices.contains(b)) prev(b)
      else Nil

    override def sources(b: Vertex): Iterable[Vertex] = childMap.getOrElse(b, Nil)

    override def tips: Iterable[Vertex] = childless

    override def insert(b: Vertex, bTargets: Iterable[Vertex]): DoublyLinkedDag.InsertResult[Vertex] =
      // Do not add a block twice
      if (allVertices.contains(b)) DoublyLinkedDag.InsertResult.AlreadyInserted()
      else {
        val missing = bTargets.filterNot(allVertices.contains)
        if (missing.nonEmpty)
          DoublyLinkedDag.InsertResult.MissingTargets(missing)
        else
          DoublyLinkedDag.InsertResult.Success(() => {
            for (block <- bTargets) {
              childMap.getOrElseUpdate(block, empty).append(b)
              // targets of new block are no longer childless
              val _ = childless.remove(block)
            }
            // Vertices must be inserted in a topological order (see check above), so
            // when a block is inserted it is not a child of any existing block.
            childMap.update(b, empty)
            childless.add(b)

            allVertices.add(b)
          })
      }
  }

  def pBlockDag(genesis: Block): DoublyLinkedDag[Block] =
    prePointerDag(genesis, _.parents)

  def jBlockDag(genesis: Block): DoublyLinkedDag[Block] =
    prePointerDag(genesis, _.justifications)
}
