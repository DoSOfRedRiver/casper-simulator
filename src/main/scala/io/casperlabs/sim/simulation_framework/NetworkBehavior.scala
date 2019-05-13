package io.casperlabs.sim.simulation_framework

import scala.util.Random

/**
  * Contract for the network delivery semantics algorithms.
  * Implementations correspond to a variety of strategies on what network delays and failures should show up.
  * Delays are decided per message.
  */
trait NetworkBehavior {

  /**
    * Tells the relative delivery delay that should be simulated for the specified agent-to-agent message.
    * This method is used to determine delivery delay for unicast (= point-to-point) communication.
    *
    * @param msg message under consideration
    * @param sender agent sending the message
    * @param destination agent where the message is supposed to be delivered
    * @param sendingTime timepoint when the message was sent
    * @return None = this message should be lost, Some(n) = it should take (n + networkLatencyLowerBound) to deliver this message
    */
  def calculateUnicastDelay(msg: Any, sender: AgentRef, destination: AgentRef, sendingTime: Timepoint): Option[TimeDelta]

  /**
    * Minimal amount of time that a delivery of agent-to-agent message can take.
    * The simulator actually uses this information to run the simulation concurrently without any sophisticated
    * Parallel-DES algorithms (like Time Warp for example).
    * Network latency lower bound effectively introduces convenient time window to execute some portions
    * of simulation in parallel, while preserving causality dependencies.
    * The bigger is the delay, the more effective the concurrency.
    */
  def networkLatencyLowerBound: TimeDelta

}

object NetworkBehavior {
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
    * @tparam MsgPayload Type of messages to send
    * @return a simple network behaviour implementation as described above.
    */
  def uniform[MsgPayload](
                           random: Random,
                           minDelay: TimeDelta,
                           maxDelay: TimeDelta,
                           dropRate: Double
                         ): NetworkBehavior =
    new NetworkBehavior {
      private val delayDelta = (maxDelay - minDelay).toInt

      override def calculateUnicastDelay(
                                          msg: Any,
                                          sender: AgentRef,
                                          destination: AgentRef,
                                          sendingTime: Timepoint
                                        ): Option[TimeDelta] = {
        val dropped = random.nextDouble() < dropRate
        if (dropped) None
        else {
          val delay = random.nextInt(delayDelta) + minDelay
          Some(delay)
        }
      }

      override def networkLatencyLowerBound: TimeDelta = minDelay
  }
}
