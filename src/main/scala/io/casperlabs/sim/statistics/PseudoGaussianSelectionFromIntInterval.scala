package io.casperlabs.sim.statistics

import scala.util.Random

/**
  * Randomly selects integer number from an interval, following the Gaussian distribution with mean
  * equal to the center of interval and standard deviation equal to 1/6 of the length of the interval.
  * We throw away results outside the interval, hence the "pseudo-gaussian".
  *
  * @param random source of randomness
  * @param from beginning of the interval (inclusive)
  * @param to end of the interval (inclusive)
  */
class PseudoGaussianSelectionFromIntInterval(random: Random, interval: (Int, Int)) {
  private val numberOfPossibleValues = interval._2 - interval._1 + 1
  require(numberOfPossibleValues >= 2)
  private val length: Double = numberOfPossibleValues.toInt
  private val mean: Double = length / 2
  private val sd: Double = length / 6

//equivalent FP implementation, but imperative version looks cleaner, so leaving the imperative one
//
//  def next(): Int = {
//    val x: Double = Stream.from(0).map(i => random.nextGaussian() * sd + mean).find(p => p >=0 && p < length).get
//    return interval._1 + x.toInt
//  }

  def next(): Int = {
    var x: Double = 0
    do {
      x = random.nextGaussian() * sd + mean
    } while (x < 0 || x >= length)
    return interval._1 + x.toInt
  }
}
