package io.casperlabs.sim.experiments

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.execution_engine.{Ether, Gas}
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.blockchain_components.hashing.FakeSha256Digester
import io.casperlabs.sim.blockchain_models.casperlabs_classic.{Genesis, Node}
import io.casperlabs.sim.sim_engine_sequential.SimulationImpl
import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.{ExternalEvent, NewAgentCreation}
import io.casperlabs.sim.simulation_framework.{Agent, AgentsCreationStream, ExternalEventsStream, NetworkBehavior, Timepoint}

import scala.util.Random

object CLClassicFixedNetwork {
//  val random = new Random
//  val fakeSha256Digester = new FakeSha256Digester(random)
//  val genesisBlock = Genesis.generate("casperlabs", fakeSha256Digester.generateHash())
//
//  val config: BlockchainConfig = new BlockchainConfig {
//
//    val accountCreationCost: Gas = 10
//    val transferCost: Gas = 2
//    val successfulBondingCost: Gas = 20
//    val refusedBondingCost: Gas = 2
//    val successfulUnbondingCost: Gas = 20
//    val refusedUnbondingCost: Gas = 2
//    val slashingCost: Gas = 1
//
//    val bondingDelay: Gas = 200
//    val unbondingDelay: Gas = 200
//    val maxStake: Ether = 1000
//    val minStake: Ether = 5
//    val minBondingUnbondingRequest: Ether = 50
//    val maxBondingRequest: Ether = 500
//    val maxUnbondingRequest: Ether = 500
//    val bondingSlidingWindowSize: Gas = 500
//    val unbondingSlidingWindowSize: Gas = 500
//    val bondingTrafficAsNumberOfRequestsLimit: Int = 5
//    val bondingTrafficAsStakeDifferenceLimit: Ether =  500
//    val unbondingTrafficAsNumberOfRequestsLimit: Int = 5
//    val unbondingTrafficAsStakeDifferenceLimit: Ether = 800
//
//    val pTimeLimitForClaimingBlockReward: Gas = 2000
//  }
//
//  def main(args: Array[String]): Unit = {
//    val nNodes = 3
//    val simEndTime = 1000L
//
//    val nodeIds = (1 to nNodes).toVector
//    val stakes = nodeIds.map(i => (i, 2 * i + 1)).toMap
//    val network = NetworkBehavior.uniform[Node.Comm](0L, 10L, 0d)
//    val sim = new SimulationImpl[Node.Comm, Node.Operation, Node.Propose.type](
//      Timepoint(simEndTime),
//      network
//    )
//    val agents = nodeIds.map(id => {
//      val local = id
//      val others = nodeIds.filterNot(_ == local).toList
//      val discovery = Discovery.fixedPool(local, others)
//      val gossip = Gossip.naive(sim, discovery, network)
//      // TODO: different propose intervals?
//      val proposeStrategy = Node.intervalPropose(10L)
//      new Node(
//        id,
//        stakes,
//        discovery,
//        gossip,
//        genesisBlock,
//        proposeStrategy,
//        config
//      )
//    })
//
//    val creation = AgentsCreationStream.fromIterator(
//      Iterator.from(0).map {
//        index =>
//          val agent: Agent[Node.Comm, Node.Operation, Node.Propose.type] =
//            if (index < nNodes) agents(index)
//            else Agent.noOp(index)
//
//          NewAgentCreation(
//            sim.nextId(),
//            agent,
//            Timepoint(index)
//          )
//      }
//    )
//
//    val external = ExternalEventsStream.fromIterator(
//      Iterator
//        .iterate(0L)(_ + 100L)
//        .map { time =>
//            ExternalEvent[Node.Comm, Node.Operation, Node.Propose.type](
//              sim.nextId(),
//              nodeIds.head,
//              Timepoint(time),
//              Node.Operation.noOp
//            )
//        }
//    )
//
//    sim.start(external, creation)
//  }
}
