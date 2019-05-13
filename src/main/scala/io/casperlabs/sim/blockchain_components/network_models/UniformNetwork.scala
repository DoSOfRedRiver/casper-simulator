package io.casperlabs.sim.blockchain_components.network_models

import io.casperlabs.sim.simulation_framework.{AgentRef, NetworkBehavior, TimeDelta, Timepoint}

import scala.util.Random

/**
  * A simple Network behaviour in which the delay is sample uniformly from
  * [minDelay, maxDelay) and there is a constant probability of the message
  * being dropped.
  *
  * @param random source of randomness
  * @param minDelay minimum network delay
  * @param maxDelay maximum network delay
  * @param dropRate number between 0 and 1 inclusive representing the probability
  *                 of a message being dropped
  * @return a simple network behaviour implementation as described above.
  */
class UniformNetwork(
                      random: Random,
                      minDelay: TimeDelta,
                      maxDelay: TimeDelta,
                      dropRate: Double
                    ) extends NetworkBehavior {

  private val delayDelta = (maxDelay - minDelay).toInt

  override def calculateUnicastDelay(
                                      msg: Any,
                                      sender: AgentRef,
                                      destination: AgentRef,
                                      sendingTime: Timepoint
                                    ): Option[TimeDelta] = {
    val dropped = random.nextDouble() < dropRate
    if (dropped)
      None
    else {
      val delay = random.nextInt(delayDelta) + minDelay
      Some(delay)
    }
  }

  override def networkLatencyLowerBound: TimeDelta = minDelay

}
