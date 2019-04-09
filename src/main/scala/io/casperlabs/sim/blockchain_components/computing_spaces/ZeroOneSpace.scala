package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}

/**
  * Minimalistic computing space, where there are only two memory states: 0 and 1.
  * Programs are all partial functions {0,1} -> {0,1}.
  * In total there are nine programs possible.
  */
object ZeroOneSpace {

  sealed abstract class MemoryState {}

  object MemoryState {
    case object Zero extends MemoryState
    case object One extends MemoryState
  }

  case class Program(valueAtZero: Option[MemoryState], valueAtOne: Option[MemoryState])

  class ComputingSpace(memorySize: Int) extends ComputingSpaceAPI[Program, MemoryState] {
    override def initialState: MemoryState = MemoryState.Zero

    override def compose(p1: Program, p2: Program): Program = {
      val pf: PartialFunction[MemoryState, Option[MemoryState]] = {
        case MemoryState.Zero => p2.valueAtZero
        case MemoryState.One => p2.valueAtOne
      }
      return Program(
        valueAtZero = p1.valueAtZero flatMap pf,
        valueAtOne = p1.valueAtOne flatMap pf
      )
    }

    override def execute(program: Program, memState: MemoryState): ProgramResult = {
      memState match {
        case MemoryState.Zero => ProgramResult(program.valueAtZero, 1)
        case MemoryState.One => ProgramResult(program.valueAtOne, 1)
      }
    }
  }

}
