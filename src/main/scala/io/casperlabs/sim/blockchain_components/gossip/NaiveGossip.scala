package io.casperlabs.sim.blockchain_components.gossip

import io.casperlabs.sim.abstract_blockchain.NodeId
import io.casperlabs.sim.blockchain_components.discovery.Discovery
import io.casperlabs.sim.simulation_framework.{AgentRef, PluggableAgentBehaviour}

/**
  * A naive (mock) implementation of gossiping which just sends the message to all direct peers.
  * There are no re-transmissions of messages.
  * This is going to work only if underlying Discovery service sees all other nodes as direct peers.
  * In practice this is a mock solution to be coupled with TrivialDiscovery.
  */
class NaiveGossip(val discovery: Discovery[NodeId, AgentRef]) extends Gossip[NodeId, AgentRef] with PluggableAgentBehaviour {

  override def gossip(msg: Any): Unit = {
    for (p <- discovery.peers)
      p !! msg
  }

  override def startup(): Unit = {
    //do nothing
  }

  override def onExternalEvent(msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

  override def onTimer(msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

  override def receive(sender: AgentRef, msg: Any): Boolean = {
    //do nothing; leave message unconsumed
    return false
  }

}

