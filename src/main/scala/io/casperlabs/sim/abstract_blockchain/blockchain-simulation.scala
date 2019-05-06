package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.{BlockId, NodeId, Transaction}
import io.casperlabs.sim.simulation_framework.Timepoint
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
  * We need this level of abstraction to compare different blockchain implementations.
  *
  * This is a stateful, process-like entity (can be controlled by starting-pausing-stopping.
  * When it runs, it:
  *   1. Consumes stream of transaction.
  *   2. Produces 3 streams of events (= block proposals, block deliveries, block finalizations).
  */
trait BlockchainSimulation extends Observable[SimulationOutputItem] with Observer [ScheduledDeploy] {
}

/**
  * Deploy to be delivered to the node (= validator).
  *
  * @param transaction transaction to be executed
  * @param deliveryTimepoint scheduled timepoint of delivery
  * @param node target node
  */
case class ScheduledDeploy(transaction: Transaction, deliveryTimepoint: Timepoint, node: NodeId)


abstract class SimulationOutputItem

object SimulationOutputItem {
  case class DeployWasDelivered(time: Timepoint, targetNode: NodeId, transaction: Transaction) extends SimulationOutputItem
  case class NewNodeJoinedTheNetwork(node: NodeId) extends SimulationOutputItem
  case class BlockWasAnnouncedViaProposed[B <: AbstractBlock](time: Timepoint, origin: NodeId, block: B) extends SimulationOutputItem
  case class NewBlockWasDeliveredViaGossip(time: Timepoint, nodeId: NodeId, blockId: BlockId) extends SimulationOutputItem
  case class BlockWasFinalized(time: Timepoint, nodeIdThatRecognizedFinalization: NodeId, block: BlockId) extends SimulationOutputItem
  case class AgentMessageWasSent(time: Timepoint, message: Any) extends SimulationOutputItem
  case class AgentMessageWasDelivered(time: Timepoint, message: Any) extends SimulationOutputItem
}
