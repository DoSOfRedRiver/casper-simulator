package io.casperlabs.sim.experiments

import io.casperlabs.sim.blockchain_components.{Discovery, Gossip}
import io.casperlabs.sim.blockchain_models.casperlabs_classic.{Genesis, Node}
import io.casperlabs.sim.sim_engine_sequential.SimulationImpl
import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.{ExternalEvent, NewAgentCreation}
import io.casperlabs.sim.simulation_framework.{AgentsCreationStream, ExternalEventsStream, NetworkBehavior, Timepoint}

object CLClassicFixedNetwork {
  def main(args: Array[String]): Unit = {
    val nNodes = 3
    val simEndTime = 1000L

    val nodeIds = (1 to nNodes).toVector
    val stakes = nodeIds.map(i => (i, 2 * i + 1)).toMap
    val network = NetworkBehavior.uniform[Node.Comm](0L, 10L, 0d)
    val sim = new SimulationImpl[Node.Comm, Node.Operation](
      Timepoint(simEndTime),
      network
    )
    val agents = nodeIds.map(id => {
      val local = id
      val others = nodeIds.filterNot(_ == local).toList
      val discovery = Discovery.fixedPool(local, others)
      val gossip = Gossip.naive(sim, discovery, network)
      new Node(
        id,
        stakes,
        discovery,
        gossip,
        Genesis
      )
    })

    val creation = AgentsCreationStream.fromIterator(
      agents.iterator.map(node => {
        NewAgentCreation(
          sim.nextId(),
          node,
          Timepoint(0L)
        )
      })
    )

    val external = ExternalEventsStream.fromIterator(
      Iterator
        .iterate(0L)(_ + 100L)
        .map { time =>
            ExternalEvent[Node.Comm, Node.Operation](
              sim.nextId(),
              nodeIds.head,
              Timepoint(time),
              Node.Operation.noOp
            )
        }
    )

    sim.start(external, creation)
  }
}
