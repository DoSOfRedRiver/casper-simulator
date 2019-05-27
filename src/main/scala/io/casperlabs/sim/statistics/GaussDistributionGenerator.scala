package io.casperlabs.sim.statistics

import scala.util.Random

class GaussDistributionGenerator(random: Random, p: GaussDistributionParams) {

  def next(): Double = random.nextGaussian() * p.standardDeviation + p.mean

}
