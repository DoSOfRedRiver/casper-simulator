package io.casperlabs.sim.data_generators

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.Gas

trait ProgramsGenerator[P, MS, CS <: ComputingSpace[P,MS]] {

  /**
    * Returns randomly-generator program with reasonably selected gas limit.
    */
  def next(): (P, Gas)

}
