package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

package object execution_engine {
  type BlockId = String
  type NodeId = Int
  type ValidatorId = Int

  type Ether = Long
  type Gas = Long
  type Account = Int
  type BlocksMultimap = Map[Block, Set[Block]]

}
