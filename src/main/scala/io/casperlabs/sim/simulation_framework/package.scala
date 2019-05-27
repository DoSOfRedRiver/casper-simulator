package io.casperlabs.sim

package object simulation_framework {
  type TimeDelta = Long
  type AgentsCreationStream[R] = Iterator[SimEventsQueueItem.NewAgentCreation[R]]
  type ExternalEventsStream = Iterator[SimEventsQueueItem.ExternalEvent]

}
