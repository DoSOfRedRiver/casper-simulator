package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.NewAgentCreation

trait AgentsCreationStream[Msg] {
  def next(): Option[NewAgentCreation[Msg]]
}

object AgentsCreationStream {
  def fromIterator[Msg](iter: Iterator[NewAgentCreation[Msg]]): AgentsCreationStream[Msg] =
    () => if (iter.hasNext) Some(iter.next()) else None

}
