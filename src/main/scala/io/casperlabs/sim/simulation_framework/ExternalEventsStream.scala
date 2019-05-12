package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.ExternalEvent

/**
  * Contract for external events generators.
  */
trait ExternalEventsStream[Msg] {
  def next(): ExternalEvent[Msg]
}

object ExternalEventsStream {
  def fromIterator[Msg](
    iter: Iterator[ExternalEvent[Msg]]
  ): ExternalEventsStream[Msg] = () => iter.next()
}
