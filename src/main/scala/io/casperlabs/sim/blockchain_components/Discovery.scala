package io.casperlabs.sim.blockchain_components

/**
  * A trait for "node discovery". I.e. this is ths trait which abstracts
  * how nodes find each other in the P2P network underlying the blockchain.
  * The production implementation of this is the Kademlia algorithm.
  * @tparam Id The type for how nodes are identified (the production version
  *            is a 32-byte hash of the node's public key certificate).
  * @tparam Endpoint The type for how nodes communicate with one another (the
  *                  production version of this is a gRPC endpoint).
  */
trait Discovery[Id, Endpoint] {
  def lookup(id: Id): Option[Endpoint]
  def peers(): List[Endpoint]
  def self: Id
}

object Discovery {
  /**
    *  A simple implementation, where no real discovery happens -- all nodes
    *  are statically known from the beginning.
    * @param local the identifier of the current node
    * @param others the identifiers for the remaining nodes
    * @tparam Id The type used to identify nodes. Since this is a simulation environment,
    *            this is also used as the endpoint type because in a simulation a node's
    *            identity is sufficient to send it a message.
    * @return A Discovery instance as described above.
    */
  def fixedPool[Id](local: Id, others: List[Id]): Discovery[Id, Id] = new Discovery[Id, Id] {
    override def lookup(id: Id): Option[Id] =
      if (id == local) Some(local)
      else others.find(_ == id)

    override def peers(): List[Id] = others

    override def self: Id = local
  }
}
