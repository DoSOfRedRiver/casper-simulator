package io.casperlabs.sim.sim_engine_akka

import akka.actor.Actor
import io.casperlabs.sim.simulation_framework.NetworkBehavior

/**
  * This actor manages the flow of a simulation.
  * Responsibilities:
  *   1. keeps the central queue of messages flying between agents
  *   2. runs the virtual clock
  *   3. pools the external events stream
  *   4. dispatches work across wrapper actors (in a way that ensures no causality violation will happen).
  */
class DispatcherActor[MsgPayload](networkBehavior: NetworkBehavior, initialCollectionOfAgents: Iterable[Actor]) extends Actor {

  override def receive: Receive = ???

}
