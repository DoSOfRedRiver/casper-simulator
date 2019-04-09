package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}

/**
  * Computing space that mimics a scaled-down version of what is found inside a typical blockchain
  * that supports smart contracts (like the CasperLabs one). We have a finitely sized memory
  * with binary cells.
  *
  * Programs are written in a "mini" programming language: there are 6 simple statements
  * (Clear, Load, Store, Write, Flip, Assert) and a program is just a sequence of statements.
  *
  * Execution of a program is sequential, and the "virtual machine" is like a processor with one
  * binary register (called "accumulator").
  */
object BinaryArraySpace {

  type CellAddress = Int

  type MemoryState = scala.collection.immutable.BitSet

  type MemoryStateBuffer = scala.collection.mutable.BitSet

  case class Program(statements: IndexedSeq[Statement])

  class ComputingSpace(memorySize: Int) extends ComputingSpaceAPI[Program, MemoryState] {
    assert(memorySize % 8 == 0)
    assert(memorySize > 0)

    override def initialState: MemoryState = scala.collection.immutable.BitSet.empty

    override def compose(p1: Program, p2: Program): Program = Program(p1.statements ++ p2.statements)

    override def execute(program: Program, memState: MemoryState): ProgramResult = {
      val workingMemState: MemoryStateBuffer = scala.collection.mutable.BitSet.fromBitMask(memState.toBitMask)
      var gasBurned: Int = 0
      var accumulator: Int = 0
      for (op <- program.statements) {
        gasBurned += 1
        op match {
          case Statement.Clear =>
            accumulator = 0
          case Statement.Load(address) =>
            accumulator = readBit(workingMemState, address)
          case Statement.Store(address) =>
            writeBit(workingMemState, address, accumulator)
          case Statement.Write(address, value) =>
            writeBit(workingMemState, address, value)
          case Statement.Flip(address) =>
            val b = readBit(workingMemState, address)
            val negated = if (b == 0) 1 else 0
            writeBit(workingMemState, address, negated)
          case Statement.Assert(address, value) =>
            if (readBit(workingMemState, address) != value)
              return ProgramResult(None, gasBurned)
        }
      }
      val finalMemoryState = scala.collection.immutable.BitSet.fromBitMaskNoCopy(workingMemState.toBitMask)
      return ProgramResult(Some(finalMemoryState), gasBurned)
    }

    private def readBit(state: MemoryStateBuffer, address: Int): Int = {
      if (state(address)) 1 else 0
    }

    private def writeBit(state: MemoryStateBuffer, address: Int, value: Int): Unit = {
      state(address) = if (value == 0) false else true
    }

  }

  sealed abstract class Statement {
  }

  object Statement {
    //sets the accumulator to zero
    case object Clear extends Statement

    //reads memory at given address, adds to the accumulator (modulo 2) and stores the result the accumulator
    case class Load(address: CellAddress) extends Statement

    //stores the value of 'accumulator' at the specified address
    case class Store(address: CellAddress) extends Statement

    //writes the value 'value' at given address
    case class Write(address: CellAddress, value: Int) extends Statement

    //adds 1 (modulo 2) to the value stored at given address
    case class Flip(address: CellAddress) extends Statement

    //reads the value at address; terminates the program abruptly if the value does not equal to 'value'
    case class Assert(address: CellAddress, value: Int) extends Statement
  }

}
