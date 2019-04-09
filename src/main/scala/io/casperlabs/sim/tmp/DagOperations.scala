package io.casperlabs.sim.tmp

import scala.collection.immutable.{HashSet, Queue}

object DagOperations {

  def bfTraverseF[A](start: List[A])(neighbours: A => List[A]): Stream[A] = {

    def build(q: Queue[A], prevVisited: HashSet[A]): Stream[A] =
      if (q.isEmpty) Stream.empty[A]
      else {
        val (curr, rest) = q.dequeue
        if (prevVisited.contains(curr))
          build(rest, prevVisited)
        else {
          val ns = neighbours(curr)
          val visited = prevVisited + curr
          val newQ = rest.enqueue[A](ns.filterNot(visited))
          curr #:: build(newQ, visited)
        }
      }

    return build(Queue.empty[A].enqueue[A](start), HashSet.empty[A])
  }

}
