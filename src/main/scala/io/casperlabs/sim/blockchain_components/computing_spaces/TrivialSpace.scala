package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}

/**
  * Trivial computing space.
  * There is only one memory state and one program in this space.
  */
object TrivialSpace {

  sealed abstract class MemoryState {}
  object MemoryState {
    case object Singleton extends MemoryState
  }

  sealed abstract class Program {}
  object Program {
    case object Singleton extends Program
  }

  object ComputingSpace extends ComputingSpaceAPI[Program, MemoryState] {

    override def initialState: MemoryState = MemoryState.Singleton

    override def compose(p1: Program, p2: Program): Program = Program.Singleton

    override def execute(program: Program, on: MemoryState): ProgramResult = ProgramResult(Some(MemoryState.Singleton), 1)
  }

}
