package io.casperlabs.sim.simulation_framework

/**
  * Contract for the network delivery semantics algorithms.
  * Implementations correspond to a variety of strategies on what network delays and failures should show up.
  * Delays are decided per message.
  */
trait NetworkBehavior[MsgPayload] {

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
  def calculateUnicastDelay(msg: MsgPayload, sender: AgentId, destination: AgentId, sendingTime: Timepoint): Option[TimeDelta]

  /**
    * Tells the relative delivery delay that should be simulated for the specified agent-to-agent message.
    * This method is used to determine delivery delay for broadcasting.
    *
    * In an ideal network we expect the message to be delivered to every agent participating in the sim.
    * To be more precise - the set of agents is dynamic (new agents can show up as the simulation goes)
    * and it is up to the implementation to decide on the subset of agents collection that should actually
    * receive the message. Also the delivery delays can be decided per-destination-agent.
    *
    * Returned map encodes both the subset of agents that will receive the message and the individual delays.
    * For example: Map(243 -> 33, 3 -> 100, 24 -> 1500) means that:
    * (1) the message should be delivered to agents 243, 3 and 24 only
    * (2) delivery time in the case of agent 234 should be 33 + networkLatencyLowerBound.
    *
    * @param msg message under consideration
    * @param sender agent sending the message
    * @param sendingTime timepoint when the message was sent
    * @return map: destination agent ----> delta
    */
  def calculateBroadcastDelay(msg: MsgPayload, sender: AgentId, sendingTime: Timepoint): Map[AgentId, TimeDelta]

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
