package io.casperlabs.sim

import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.simulation_framework.AgentRef

package object abstract_blockchain {
  type BlockId = Hash
  type NodeId = Int
  type ValidatorId = String

}
