package io.casperlabs.sim.blockchain_components.graphs

import scala.collection.mutable
import collection.mutable.{ HashMap, MultiMap, Set }

/**
  * Represents two-argument relations (= subsets of cartesian product AxB).
  * This can also be seen as sparse matrix.
  * Technically this is equivalent to mutable.Set[(A,B)], but we do smart indexing, so to have
  * rows and columns access with O(1) complexity.
  * Also can be seen as bidirectional (mutable) multimap.
  * Also can be seen as directed graph representation.
  *
  * Because relations generalize functions, type A is like "domain" and type B is like "codomain".
  * Elements in A we call 'sources', elements in B we call 'targets'.
  */
class IndexedTwoArgRelation[A,B] {
  private val ab = new mutable.HashMap[A, Set[B]] with mutable.MultiMap[A,B]
  private val ba = new mutable.HashMap[B, Set[A]] with mutable.MultiMap[B,A]

  def addPair(a: A, b: B): Unit = {
    ab.addBinding(a,b)
    ba.addBinding(b,a)
  }

  def removePair(a: A, b: B): Unit = {
    ab.removeBinding(a,b)
    ba.removeBinding(b,a)
  }

  def removeSource(a: A): Unit =
    for (b <- this.findTargetsFor(a))
      removePair(a,b)

  def removeTarget(b: B): Unit =
    for (a <- this.findSourcesFor(b))
      removePair(a,b)

  def containsPair(a: A, b: B): Boolean =
    ab.get(a) match {
      case Some(set) => set.contains(b)
      case None => false
    }

  def findTargetsFor(source: A): Iterable[B] =
    ab.get(source) match {
      case Some(set) => set
      case None => Set.empty
    }

  def findSourcesFor(target: B): Iterable[A] =
    ba.get(target) match {
      case Some(set) => set
      case None => Set.empty
    }

  def hasSource(a: A): Boolean = ab.contains(a)

  def hasTarget(b: B): Boolean = ba.contains(b)

}
