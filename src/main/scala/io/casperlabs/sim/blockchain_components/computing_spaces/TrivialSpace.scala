package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}
import io.casperlabs.sim.blockchain_components.execution_engine.Gas
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

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

    override def execute(program: Program, on: MemoryState, gasLimit: Gas): ProgramResult = ProgramResult.Success(MemoryState.Singleton, 1)

    override def updateDigestWithMemState(ms: MemoryState, digest: CryptographicDigester): Unit = {
      //do nothing because there is only one memory state
    }

    override def updateDigestWithProgram(p: Program, digest: CryptographicDigester): Unit = {
      //do nothing updateDigestWithProgram there is only one program
    }
  }

}
