package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.NewAgentCreation

trait AgentsCreationStream[MsgPayload, ExtEventPayload, PrivatePayload] {
  def next(): NewAgentCreation[MsgPayload,ExtEventPayload, PrivatePayload]
}

object AgentsCreationStream {
  def fromIterator[MsgPayload, ExtEventPayload, PrivatePayload](
    iter: Iterator[NewAgentCreation[MsgPayload, ExtEventPayload, PrivatePayload]]
  ): AgentsCreationStream[MsgPayload, ExtEventPayload, PrivatePayload] = () => iter.next()
}
