package io.casperlabs.sim.sim_engine_akka

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.casperlabs.sim.simulation_framework.{Agent, SimEventsQueueItem, Simulation, Timepoint}

/**
  * Simulation engine impl based on akka.
  * We arrange here the actor system and create actors that run the simulation.
  */
class SimulationImpl[MsgPayload, ExtEventPayload] extends Simulation[MsgPayload, ExtEventPayload] {
  private val actorSystem = ActorSystem("sim", ConfigFactory.load("sim"))
  private val idGenerator = Iterator.iterate(0L)(_ + 1L)

  override def nextId(): Long = idGenerator.next()

  override def currentTime(): Timepoint = ???

  override def registerCommunication(event: SimEventsQueueItem.AgentToAgentMsg[MsgPayload, ExtEventPayload]): Unit = ???

  override def registerAgent(agent: Agent[MsgPayload, ExtEventPayload]): Unit = ???

  override def start(): Unit = ???
}
