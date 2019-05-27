package io.casperlabs.sim.blockchain_components.gossip

import io.casperlabs.sim.blockchain_components.discovery.Discovery

/**
  * Trait abstracting over how nodes in the P2P network propagate information
  * across the network. In production, this will be the algorithm developed by Akosh,
  * see https://techspec.casperlabs.io/technical-details/global-state/communications#block-gossiping.
  *
  * Technically we need this trait to experiment with different implementations of gossiping in the blockchain network.
  *
  * Caution: instance of Gossip is supposed to run in the context of a single node (=agent).
  *
  * @tparam Nid Identity type of nodes in the network (see Discovery documentation)
  * @tparam Endpoint Type for endpoints of node-to-node communication (see Discovery Documentation)
  */
trait Gossip[Nid, Endpoint] {

  /**
    * Discovery instance used by this gossip.
    */
  def discovery: Discovery[Nid, Endpoint]

  //todo: figure out if we really need direct messaging to exist on this level (?)
//  def direct(peer: Endpoint, msg: MsgPayload): Gossip.Response[MsgPayload]

  /**
    * Broadcast given message to all the nodes in P2P network.
    * Caution: in general this is a best-effort broadcasting. No delivery order or delivery guarantees are imposed
    * by this contract.
    *
    * @param msg message to be broadcasted
    */
  def gossip(msg: Any): Unit

}
