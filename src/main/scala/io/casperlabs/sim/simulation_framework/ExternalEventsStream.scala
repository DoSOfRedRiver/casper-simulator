package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.ExternalEvent

/**
  * Contract for external events generators.
  */
trait ExternalEventsStream[MsgPayload, ExtEventPayload] {
  def next(): ExternalEvent[MsgPayload, ExtEventPayload]
}
