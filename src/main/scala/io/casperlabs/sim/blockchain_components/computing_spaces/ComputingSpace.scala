package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.execution_engine.Gas

/**
  * Abstraction of "programming" layer of a blockchain, so the part where smart contracts live.
  * We just abstract away from the programming and shared memory by just saying:
  * 1. There is some type representing memory states.
  * 2. There is another type representing programs.
  * 3. Execution of a program can be seen as a transition from one memory state to another.
  * 4. Programs can be composed.
  * 5. Programs may fail.
  * 6. Program executing has it cost (expressed in integer units called Gas).
  * 7. Execution cost depends on the program and the memory state it was executed against.
  *
  * (3) + (4) + (5) together can be summarized as: programs are partial functions from the set of all memory states to itself.
  *
  * @tparam Program type representing programs
  * @tparam MemoryState type representing memory states
  */
trait ComputingSpace[Program, MemoryState] {

  /**
    * This is the initial state, so a state that a new blockchain has on creation (= before any program has been executed).
    */
  def initialState: MemoryState


  /**
    * Calculates a program that is a composition of given two programs (p1 is to be executed first, then p2).
    *
    * @param p1 program going to be executed as first in the pair
    * @param p2 program going to be executed as second in the pair
    * @return result of the composition
    */
  def compose(p1: Program, p2: Program): Program

  /**
    * Executes a program against given memory state.
    *
    * @param program program to be executed
    * @param memState memory state of a blockchain computer at the moment before starting of the program
    * @return ProgramResult(Some(ms), gas) - represent successful execution result with final memory state ms and specified amount of gas burned
    *         ProgramResult(None, gas) - represents failure of the program; failures also consume gas, so the amount of gas burned is here as well
    */
  def execute(program: Program, memState: MemoryState, gasLimit: Gas): ProgramResult

  abstract class ProgramResult

  object ProgramResult {
    case class Success(ms: MemoryState, gasUsed: Gas) extends ProgramResult
    case class Crash(gasUsed: Gas) extends ProgramResult
    case object GasLimitExceeded extends ProgramResult
  }

}

