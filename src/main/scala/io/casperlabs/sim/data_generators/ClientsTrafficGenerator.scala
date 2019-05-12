package io.casperlabs.sim.data_generators

import io.casperlabs.sim.abstract_blockchain.ScheduledDeploy
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.NodeId

/**
  * Simulates the traffic coming to the blockchain network from clients.
  * This is modelled as a stream of ScheduledDeploy instances.
  *
  * This is a simple generator that does not support attack scenarios.
  * We generate
  */
class ClientsTrafficGenerator[P, MS, CS <: ComputingSpace[P,MS]](
                                nodes: IndexedSeq[NodeId],
                                relativeTrafficIntensityPerNode: Map[NodeId, Double],
                                deploysPerSecond: Double,
                                transactionsGenerator: TransactionsGenerator[P,MS,CS]
                             ) {



  def next(): ScheduledDeploy = {
    ???
  }

}
