package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.blockchain_components.hashing.{FakeHash, Hash}
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block
import io.casperlabs.sim.simulation_framework.AgentRef

package object execution_engine {
  type Ether = Long
  type Gas = Long
  type Account = Int
}
