package io.casperlabs.sim.blockchain_components.discovery

/**
  * A trait for "node discovery" in a P2P network.
  *
  * This is ths trait which abstracts how nodes find each other in the P2P network underlying the blockchain.
  * The production implementation of this is the Kademlia algorithm.
  *
  * Technically we need this trait to experiment with different implementations of discovery in the blockchain.
  *
  * Caution: instance of Discovery is supposed to run in the context of a single node (=agent).
  *
  * @tparam Nid The type for how nodes are identified (the production version
  *            is a 32-byte hash of the node's public key certificate, in simulator this is 32-byte random hash).
  * @tparam Endpoint The type for how nodes communicate with one another (the
  *                  production version of this is a gRPC endpoint, in simulator we use AgentRef instead).
  */
trait Discovery[Nid, Endpoint] {

  /**
    * Attempt finding the endpoint for a node.
    *
    * @param id id of a node we are looking for
    * @return None it the attempt failed (does not mean that such a node is non-existent !)
    */
  def lookup(id: Nid): Option[Endpoint]

  /**
    * Collection of currently known "direct" peers.
    */
  def peers: List[Endpoint]

  /**
    * The id of a node running this instance of discovery, i.e. this is just the "local" node.
    */
  def selfNodeId: Nid

}
