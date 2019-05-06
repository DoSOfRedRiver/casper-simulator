package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}
import io.casperlabs.sim.blockchain_components.execution_engine.Gas

/**
  * Trivial computing space extended so that desired transaction execution and cost is encoded inside the transaction.
  */
object MockingSpace {

  sealed abstract class MemoryState {}
  object MemoryState {
    case object Singleton extends MemoryState
  }

  sealed abstract class Program {}
  object Program {
    case class Happy(gasToBeUsed: Gas) extends Program
    case object Looping extends Program
    case class Crashing(gasToBeUsed: Gas) extends Program
  }

  object ComputingSpace extends ComputingSpaceAPI[Program, MemoryState] {

    override def initialState: MemoryState = MemoryState.Singleton

    override def compose(p1: Program, p2: Program): Program =
      (p1,p2) match {
        case (Program.Happy(gas1), Program.Happy(gas2)) => Program.Happy(gas1 + gas2)
        case (Program.Happy(gas), Program.Looping) => Program.Looping
        case (Program.Happy(gas1), Program.Crashing(gas2)) => Program.Crashing(gas1 + gas2)
        case (Program.Looping, _) => Program.Looping
        case (Program.Crashing(gas), _) => Program.Crashing(gas)
      }

    override def execute(program: Program, on: MemoryState, gasLimit: Gas): ProgramResult =
      program match {
        case Program.Happy(gas) => ProgramResult.Success(MemoryState.Singleton, gas)
        case Program.Looping => ProgramResult.GasLimitExceeded
        case Program.Crashing(gas) => ProgramResult.Crash(gas)
      }

  }


}
