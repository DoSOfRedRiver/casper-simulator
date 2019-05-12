package io.casperlabs.sim.data_generators

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace

trait ProgramsGenerator[P, MS, CS <: ComputingSpace[P,MS]] {
  def next: P
}
