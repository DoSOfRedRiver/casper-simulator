package io.casperlabs.sim.blockchain_models

package object casperlabs {

  type BlockId = String
  type Slot = Int
  type Ether = Int
  type Gas = Int
  type Account = Int
  type BlocksMultimap = Map[Block, Set[Block]]

}
