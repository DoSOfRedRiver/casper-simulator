package io.casperlabs.sim.blockchain_components

import io.casperlabs.sim.blockchain_models.casperlabs_classic.Block

package object execution_engine {
  type BlockId = String
  type NodeId = Int

  type Ether = Int
  type Gas = Int
  type Account = Int
  type BlocksMultimap = Map[Block, Set[Block]]

}
