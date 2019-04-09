package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.{Ether, Gas}

trait BlockchainConfig {
  def minimalValidatorStake: Ether
  def accountCreationCost: Gas

}
