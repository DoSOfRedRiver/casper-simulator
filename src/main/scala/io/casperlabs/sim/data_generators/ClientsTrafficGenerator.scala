package io.casperlabs.sim.data_generators

import io.casperlabs.sim.abstract_blockchain.{NodeId, ScheduledDeploy}
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.{Account, Ether, Transaction}
import io.casperlabs.sim.simulation_framework.{AgentRef, Timepoint}

import scala.util.Random

/**
  * Simulates the traffic coming to the blockchain network from clients.
  * This is modelled as a stream of ScheduledDeploy instances.
  *
  * Caution: this is a simple generator that does not support attack scenarios.
  */
class ClientsTrafficGenerator[P, MS, CS <: ComputingSpace[P, MS]](
                                                                   random: Random,
                                                                   trafficPerNode: Map[NodeId, Double],
                                                                   initialAccounts: Map[Account, Ether],
                                                                   nodes: IndexedSeq[AgentRef],
                                                                   deploysPerSecond: Double,
                                                                   transactionsGenerator: TransactionsGenerator[P, MS, CS]
                                                                 ) {

  private val lambda: Double = deploysPerSecond / 1000000 //rescaling to microseconds
  private var clock: Long = 0L
  private val eventIdGenerator = Iterator.iterate(0L)(_ + 1L)

  def next(): ScheduledDeploy = {
    val targetNode = nodes(random.nextInt(nodes.length)) //todo: use 'trafficPerNode' here
    val when = nextPoissonTimepoint()
    var transaction: Option[Transaction] = None
    while (transaction.isEmpty) {
      transaction = transactionsGenerator.createTransaction()
    }

    return ScheduledDeploy(eventIdGenerator.next(), transaction.get, when, targetNode)
  }

  def nextPoissonTimepoint(): Timepoint = {
    val point: Double = - math.log(random.nextDouble()) / lambda
    clock = clock + point.toLong
    return Timepoint(clock)
  }

}

