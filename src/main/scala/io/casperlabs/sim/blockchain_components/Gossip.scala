package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.AgentToAgentMsg
import io.casperlabs.sim.simulation_framework.{AgentId, NetworkBehavior, Simulation}

/**
  * Trait abstracting over how nodes in the P2P network propagate information
  * across the network. In production, this will be the algorithm developed by Akosh,
  * see https://techspec.casperlabs.io/technical-details/global-state/communications#block-gossiping
  *
  * @tparam Id Identity type of nodes in the network (see Discovery documentation)
  * @tparam Endpoint Type for endpoints of node-to-node communication (see Discovery Documentation)
  * @tparam MsgPayload Type of the messages used to communicate between nodes.
  */
trait Gossip[Id, Endpoint, MsgPayload] {
  def discovery: Discovery[Id, Endpoint]
  def direct(peer: Endpoint, msg: MsgPayload): Gossip.Response[MsgPayload]
  def gossip(msg: MsgPayload): Unit
}

object Gossip {
  // TODO: Figure out how to do two way communication.
  // This is a placeholder for now.
  class Response[MsgPayload]

  /**
    * A naive implementation of gossiping which just sends the message to all peers.
    * @param s simulation framework (need for creating and registering events corresponding to communication)
    * @param d node discovery (needed for having a notion of peers)
    * @param n network layer (needed for computing delay/drop of message)
    * @tparam MsgPayload Type of the message being sent between nodes
    * @tparam ExtEventPayload Type of the message coming to nodes from the outside
    * @return Naive gossiping service
    */
  def naive[MsgPayload, ExtEventPayload](s: Simulation[MsgPayload, ExtEventPayload],
                                         d: Discovery[AgentId, AgentId],
                                         n: NetworkBehavior[MsgPayload]
                                        ): Gossip[AgentId, AgentId, MsgPayload] =
    new Gossip[AgentId, AgentId, MsgPayload] {
      override def discovery: Discovery[AgentId, AgentId] = d

      override def direct(peer: AgentId, msg: MsgPayload): Gossip.Response[MsgPayload] = {
        // FIXME
        new Response[MsgPayload]
      }

      override def gossip(msg: MsgPayload): Unit = {
        val sentTime = s.currentTime()
        d.peers().flatMap(peer =>
          for {
            delay <- n.calculateUnicastDelay(msg, d.self, peer, sentTime)
            arrivalTime = sentTime + delay
          } yield AgentToAgentMsg[MsgPayload, ExtEventPayload](
            ???, // TODO: id generator?
            d.self,
            peer,
            sentTime,
            arrivalTime,
            msg
          )
        ).foreach(s.registerCommunication)
      }
    }
}
