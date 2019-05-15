package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.abstract_blockchain.BlockchainSimulationOutputItem

abstract class SimPlatformRecordingItem {
}

object SimPlatformRecordingItem {
  case class AgentMessageWasSent(time: Timepoint, message: Any) extends BlockchainSimulationOutputItem
  case class AgentMessageWasDelivered(time: Timepoint, message: Any) extends BlockchainSimulationOutputItem
  case class AgentWasCreated(time: Timepoint, notSureWhatHere: Any) extends BlockchainSimulationOutputItem
  case class ExternalEventWasDelivered(time: Timepoint, toWhichAgent: AgentRef, something: Any) extends BlockchainSimulationOutputItem
}
