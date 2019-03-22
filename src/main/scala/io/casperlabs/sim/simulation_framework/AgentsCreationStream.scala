package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.NewAgentCreation

trait AgentsCreationStream[MsgPayload, ExtEventPayload] {
  def next(): NewAgentCreation[MsgPayload,ExtEventPayload]

}
