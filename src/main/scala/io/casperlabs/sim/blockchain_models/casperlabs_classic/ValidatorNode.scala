package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, BlockchainSimulationOutputItem, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.discovery.Discovery
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.simulation_framework.{AbstractAgentWithPluggableBehaviours, AgentRef, PluggableAgentBehaviour}

class ValidatorNode(
                     config: BlockchainConfig,
                     nodeId: NodeId,
                     label: String,
                     genesis: Genesis,
                     discovery: Discovery[NodeId, AgentRef] with PluggableAgentBehaviour,
                     gossip: Gossip[NodeId, AgentRef] with PluggableAgentBehaviour,
                     validator: Validator
               ) extends AbstractAgentWithPluggableBehaviours[BlockchainSimulationOutputItem](label, plugins = List(discovery, gossip, validator)) {

}
