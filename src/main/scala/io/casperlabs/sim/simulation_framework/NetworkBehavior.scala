package io.casperlabs.sim.simulation_framework

/**
  * Contract for the network delivery semantics algorithms.
  * Implementations correspond to a variety of strategies on what network delays and failures should show up.
  * Delays are decided per message.
  */
trait NetworkBehavior[Msg] {

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
  def calculateUnicastDelay(msg: Msg, sender: AgentId, destination: AgentId, sendingTime: Timepoint): Option[TimeDelta]

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
    * @param minDelay minimum network delay
    * @param maxDelay maxmum network delay
    * @param dropRate number between 0 and 1 inclusive representing the probability
    *                 of a message being dropped
    * @tparam MsgPayload Type of messages to send
    * @return a simple network behaviour implementation as described above.
    */
  def uniform[MsgPayload](
                           minDelay: TimeDelta,
                           maxDelay: TimeDelta,
                           dropRate: Double
                         ): NetworkBehavior[MsgPayload] =
    new NetworkBehavior[MsgPayload] {
      private val rng = new scala.util.Random
      private val delayDelta = (maxDelay - minDelay).toInt

      override def calculateUnicastDelay(
                                          msg: MsgPayload,
                                          sender: AgentId,
                                          destination: AgentId,
                                          sendingTime: Timepoint
                                        ): Option[TimeDelta] = {
        val dropped = rng.nextDouble() < dropRate
        if (dropped) None
        else {
          val delay = rng.nextInt(delayDelta) + minDelay
          Some(delay)
        }
      }

      override def networkLatencyLowerBound: TimeDelta = minDelay
  }
}
