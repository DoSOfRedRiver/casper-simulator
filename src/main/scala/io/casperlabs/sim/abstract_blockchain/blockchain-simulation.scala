package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.Transaction
import io.casperlabs.sim.simulation_framework.{AgentRef, Timepoint}
import rx.{Observable, Observer}

/**
  * Provides unified was of producing new simulations of blockchains.
  *
  * Implementations are responsible for:
  *   1. building a suitable blockchain infrastructure (i.e. a network of validators exchanging messages)
  *   2. deciding on how the network of validators is growing/shrinking over time.
  *   3. deciding on the physical layer of network that validators use.
  *   4. preparing a suitable simulation of clients, so for example deciding on "when a given transaction T is deployed,
  *      and towards which validator the deploy request is sent.
  */
trait BlockchainSimulationFactory[MS] {
  def createNewSimulation(config: BlockchainConfig): BlockchainSimulation
}

/**
  * Abstraction of a blockchain simulation (as seen in the simulator).
  * We need this level of abstraction to be able to compare different blockchain implementations.
  */
trait BlockchainSimulation extends Observable[BlockchainSimulationOutputItem] with Observer [ScheduledDeploy] {
}

/**
  * Deploy to be delivered to the node (= validator).
  *
  * @param transaction transaction to be executed
  * @param deliveryTimepoint scheduled timepoint of delivery
  * @param node target node
  */
case class ScheduledDeploy(id: Long, transaction: Transaction, deliveryTimepoint: Timepoint, node: AgentRef)

abstract class BlockchainSimulationOutputItem

object BlockchainSimulationOutputItem {
  case class DeployWasDelivered(time: Timepoint, targetNode: NodeId, transaction: Transaction) extends BlockchainSimulationOutputItem
  case class NewNodeJoinedTheNetwork(node: NodeId) extends BlockchainSimulationOutputItem
  case class BlockWasAnnouncedViaProposed[B <: AbstractBlock](time: Timepoint, origin: NodeId, block: B) extends BlockchainSimulationOutputItem
  case class NewBlockWasDeliveredViaGossip(time: Timepoint, nodeId: NodeId, blockId: BlockId) extends BlockchainSimulationOutputItem
  case class BlockWasFinalized(time: Timepoint, nodeIdThatRecognizedFinalization: NodeId, block: BlockId) extends BlockchainSimulationOutputItem
}
