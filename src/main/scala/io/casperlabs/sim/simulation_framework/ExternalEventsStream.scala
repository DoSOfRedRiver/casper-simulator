package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.ExternalEvent

/**
  * Contract for external events generators.
  */
trait ExternalEventsStream[MsgPayload, ExtEventPayload] {
  def next(): ExternalEvent[MsgPayload, ExtEventPayload]
}

object ExternalEventsStream {
  def fromIterator[MsgPayload, ExtEventPayload](
    iter: Iterator[ExternalEvent[MsgPayload, ExtEventPayload]]
  ): ExternalEventsStream[MsgPayload, ExtEventPayload] = () => iter.next()
}
