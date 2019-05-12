package io.casperlabs.sim.data_generators

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.NodeId

/**
  * Generates random transactions.
  * This is for generating traffic of blockchain clients.
  *
  * Caution: we only generate transactions (= deploys) here.
  * The decisions on when and to which node a transaction should be delivered is going to happen
  * in a different layer (see ClientsTrafficGenerator).
  */
class TransactionsGenerator[P, MS, CS <: ComputingSpace[P,MS]](
        relativeFrequenciesOfTransactions: Map[NodeId, Double]) {


}
