package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}
import io.casperlabs.sim.blockchain_components.execution_engine.Gas
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

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

    override def execute(program: Program, memState: MemoryState, gasLimit: Gas): ProgramResult = {
      val resultingMemState: Option[MemoryState] = memState match {
        case MemoryState.Zero => program.valueAtZero
        case MemoryState.One => program.valueAtOne
      }

      resultingMemState match {
        case Some(ms) => ProgramResult.Success(ms, 1)
        case None => ProgramResult.Crash(1)
      }
    }

    override def updateDigestWithMemState(ms: MemoryState, digest: CryptographicDigester): Unit = {
      ms match {
        case MemoryState.Zero => digest.updateWith(0.toByte)
        case MemoryState.One => digest.updateWith(1.toByte)
      }
    }

    override def updateDigestWithProgram(p: Program, digest: CryptographicDigester): Unit = {
      p.valueAtZero match {
        case Some(MemoryState.Zero) => digest.updateWith(0x01.toByte)
        case Some(MemoryState.One) => digest.updateWith(0x02.toByte)
        case None => digest.updateWith(0x03.toByte)
      }
      p.valueAtOne match {
        case Some(MemoryState.Zero) => digest.updateWith(0x04.toByte)
        case Some(MemoryState.One) => digest.updateWith(0x05.toByte)
        case None => digest.updateWith(0x06.toByte)
      }
    }

  }

}
