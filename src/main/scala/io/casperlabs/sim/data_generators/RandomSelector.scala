package io.casperlabs.sim.data_generators

import scala.reflect.ClassTag
import scala.util.Random

/**
  * Select randomly one object from a collection, where the expected probability (=frequency) of each element
  * of the collection is predefined.
  *
  * @param freqMap frequency table (it gets automatically normalized, we only require that the sum of relative frequencies
  *                * is a reasonably large number (> 0.00001)
  * @param random source of randomness
  * @param tag
  * @tparam T type of elements to be selected
  */
class RandomSelector[T](freqMap: Map[T, Double], random: Random)(implicit tag: ClassTag[T]) {
  //we use primitive arrays here to achieve best performance and smallest memory footprint
  private val (items, partialSums): (Array[T], Array[Double]) = this.initializeTables(freqMap)

  def next(): T = {
    val randomPointFrom01Interval = random.nextDouble()
    for (i <- partialSums.indices)
      if (randomPointFrom01Interval < partialSums(i))
        return items(i)

    return items.last
  }

  private def initializeTables(freqMap: Map[T, Double])(implicit tag: ClassTag[T]): (Array[T], Array[Double]) = {
    if (freqMap.isEmpty)
      throw new RuntimeException("Empty frequencies map in random selector")

    val pairsOrderedByDescendingFreq: Array[(T, Double)] = freqMap.toArray.sortBy(pair => pair._2).reverse
    val items: Array[T] = pairsOrderedByDescendingFreq.map(pair => pair._1).toArray[T]
    val freqTable: Seq[Double] = pairsOrderedByDescendingFreq.map(pair => pair._2)
    val freqSum: Double = freqTable.sum
    if (freqSum < 0.00001)
      throw new RuntimeException("Invalid freq map in random selector - sum is (almost) zero")

    val partialSums = new Array[Double](freqTable.length)
    var sumAccumulator: Double = 0
    for (i <- items.indices) {
      sumAccumulator += freqTable(i) / freqSum
      partialSums(i) = sumAccumulator
    }

    return (items, partialSums)
  }

}

