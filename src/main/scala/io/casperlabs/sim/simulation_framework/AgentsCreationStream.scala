package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.NewAgentCreation

trait AgentsCreationStream[MsgPayload, ExtEventPayload] {
  def next(): NewAgentCreation[MsgPayload,ExtEventPayload]
}

object AgentsCreationStream {
  def fromIterator[MsgPayload, ExtEventPayload](
    iter: Iterator[NewAgentCreation[MsgPayload, ExtEventPayload]]
  ): AgentsCreationStream[MsgPayload, ExtEventPayload] = () => iter.next()
}
