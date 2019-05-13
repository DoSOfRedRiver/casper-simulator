package io.casperlabs.sim

package object simulation_framework {
  type TimeDelta = Long
  type AgentsCreationStream = Iterator[SimEventsQueueItem.NewAgentCreation]
  type ExternalEventsStream = Iterator[SimEventsQueueItem.ExternalEvent]

}
