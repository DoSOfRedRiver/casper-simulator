package io.casperlabs.sim.sim_engine_akka

import akka.actor.Actor
import io.casperlabs.sim.simulation_framework.Agent

/**
  * Wraps a single agent.
  */
class AgentWrapperActor[MsgPayload, ExtEventPayload, PrivatePayload](agent: Agent[MsgPayload, ExtEventPayload, PrivatePayload]) extends Actor {

  override def receive: Receive = {
    case _ => ???
  }
}
