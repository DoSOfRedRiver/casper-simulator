package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.ExternalEvent

/**
  * Contract for external events generators.
  */
trait ExternalEventsStream[MsgPayload, ExtEventPayload, PrivatePayload] {
  def next(): ExternalEvent[MsgPayload, ExtEventPayload, PrivatePayload]
}

object ExternalEventsStream {
  def fromIterator[MsgPayload, ExtEventPayload, PrivatePayload](
    iter: Iterator[ExternalEvent[MsgPayload, ExtEventPayload, PrivatePayload]]
  ): ExternalEventsStream[MsgPayload, ExtEventPayload, PrivatePayload] = () => iter.next()
}
