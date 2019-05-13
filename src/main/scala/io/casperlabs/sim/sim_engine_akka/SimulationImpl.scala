package io.casperlabs.sim.sim_engine_akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.casperlabs.sim.simulation_framework._

/**
  * Simulation engine impl based on akka.
  * We arrange here the actor system and create actors that run the simulation.
  */
class SimulationImpl[Msg] extends Simulation {
  private val actorSystem = ActorSystem("sim", ConfigFactory.load("sim"))
  private val idGenerator = Iterator.iterate(0L)(_ + 1L)

  override def currentTime(): Timepoint = ???

  override def start(externalEventsGenerator: ExternalEventsStream, agentsCreationStream: AgentsCreationStream): Unit = ???

}
