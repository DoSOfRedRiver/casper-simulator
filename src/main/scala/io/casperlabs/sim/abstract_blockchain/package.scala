package io.casperlabs.sim

import io.casperlabs.sim.blockchain_components.hashing.Hash

package object abstract_blockchain {
  type BlockId = Hash
  type NodeId = Hash
  type ValidatorId = Int

}
