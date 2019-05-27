package io.casperlabs.sim.data_generators

import io.casperlabs.sim.BaseSpec

import scala.util.Random

class RandomSelectorSpec extends BaseSpec {

  def intervalCheckWithTolerance(testedValue: Double, intervalLeftEnd: Double, intervalRightEnd: Double, toleranceAsPercentOfIntervalLength: Double): Boolean = {
    val intervalLength = intervalRightEnd - intervalLeftEnd
    val toleranceAsAbsoluteValue = intervalLength * toleranceAsPercentOfIntervalLength / 100
    return testedValue >= intervalLeftEnd - toleranceAsAbsoluteValue && testedValue <= intervalRightEnd + toleranceAsAbsoluteValue
  }

  "random selector" should "generate stream of results with expected average frequencies" in {
    val freqMap: Map[Int, Double] = Map(0 -> 1, 1 -> 5, 2 -> 4)
    val randomSelector = new RandomSelector(new Random(42), freqMap) //using fixed seed so the test results are deterministic
    val histogram = new Array[Int](3)
    val n = 1000000
    for (i <- 1 to n) {
      val generatedValue = randomSelector.next()
      histogram(generatedValue) += 1
    }

    intervalCheckWithTolerance(histogram(0) / n, intervalLeftEnd = 0.0, intervalRightEnd = 0.1, toleranceAsPercentOfIntervalLength = 1) shouldEqual true
    intervalCheckWithTolerance(histogram(1) / n, intervalLeftEnd = 0.0, intervalRightEnd = 0.5, toleranceAsPercentOfIntervalLength = 1) shouldEqual true
    intervalCheckWithTolerance(histogram(2) / n, intervalLeftEnd = 0.0, intervalRightEnd = 0.4, toleranceAsPercentOfIntervalLength = 1) shouldEqual true
  }

}
