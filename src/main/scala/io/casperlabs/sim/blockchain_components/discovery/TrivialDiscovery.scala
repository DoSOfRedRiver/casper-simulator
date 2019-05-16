package io.casperlabs.sim.blockchain_components.discovery

import io.casperlabs.sim.abstract_blockchain.NodeId
import io.casperlabs.sim.simulation_framework.{AgentRef, PluggableAgentBehaviour}

/**
  * A naive (mock) implementation, where no real discovery happens -- all nodes are statically known from the beginning.
  */
class TrivialDiscovery(val selfNodeId: NodeId, addressMap: Map[NodeId, String]) extends Discovery[NodeId, AgentRef] with PluggableAgentBehaviour {

  def lookup(id: NodeId): Option[AgentRef] = addressMap.get(id).flatMap(label => context.findAgent(label))

  lazy val peers: List[AgentRef] = addressMap.values.map(label => context.findAgent(label).get).toList

  def startup(): Unit = {
    //do nothing
  }

  def onExternalEvent(msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

  def onTimer(msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

  def receive(sender: AgentRef, msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

}
