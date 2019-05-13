package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.discovery.Discovery
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.simulation_framework.{AbstractAgentWithPluggableBehaviours, AgentRef, PluggableAgentBehaviour}

class ValidatorNode(
                     config: BlockchainConfig,
                     nodeId: NodeId,
                     ref: AgentRef,
                     label: String,
                     genesis: Genesis,
                     discovery: Discovery[ValidatorId, AgentRef] with PluggableAgentBehaviour,
                     gossip: Gossip[ValidatorId, AgentRef] with PluggableAgentBehaviour,
                     validator: Validator
               ) extends AbstractAgentWithPluggableBehaviours(ref, label, plugins = List(discovery, gossip, validator)) {

}
