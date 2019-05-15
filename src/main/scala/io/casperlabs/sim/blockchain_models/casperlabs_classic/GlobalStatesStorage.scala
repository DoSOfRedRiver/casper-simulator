package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.ExecutionEngine
import io.casperlabs.sim.blockchain_components.execution_engine.GlobalState
import io.casperlabs.sim.blockchain_components.hashing.Hash

import scala.collection.mutable

/**
  * Keeps global states snapshots, addressed by hash.
  * This way we can share this storage between nodes,
  * without a risk of violating their isolation.
  */
class GlobalStatesStorage[MS, T](ee: ExecutionEngine[MS,T]) {
  //todo: this is MVP-level solution; to be replaced by Patricia-tree (or similar) solution - we want to utilize DAG structure
  //todo: for order-of-magnitude better memory utilization

  //we do not use ConcurrentHashMap here because for now the only simulation engine is sequential
  private val storage: mutable.Map[Hash, GlobalState[MS]] = new mutable.HashMap[Hash, GlobalState[MS]]()

  def store(gs: GlobalState[MS]): Unit = {
    val hash = ee.globalStateHash(gs)
    storage.put(hash, gs)
  }

  def read(hash: Hash): Option[GlobalState[MS]] = storage.get(hash)

}
