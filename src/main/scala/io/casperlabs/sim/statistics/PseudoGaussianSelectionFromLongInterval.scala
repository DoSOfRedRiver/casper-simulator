package io.casperlabs.sim.statistics

import scala.util.Random

class PseudoGaussianSelectionFromLongInterval(random: Random, interval: (Long, Long)) {
  private val numberOfPossibleValues = interval._2 - interval._1 + 1
  require(numberOfPossibleValues >= 2)
  private val length: Double = numberOfPossibleValues.toLong
  private val mean: Double = length / 2
  private val sd: Double = length / 6

  def next(): Long = {
    var x: Double = 0
    do {
      x = random.nextGaussian() * sd + mean
    } while (x < 0L || x >= length)
    return interval._1 + x.toLong
  }

}
