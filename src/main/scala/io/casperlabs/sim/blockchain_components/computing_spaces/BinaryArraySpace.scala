package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}
import io.casperlabs.sim.blockchain_components.execution_engine.Gas
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

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
  *
  * This computation space is reach enough to reproduce all the problems and patterns we struggle with
  * in the production environment (in particular merging with op-algebra). It is also simple enough
  * to allow for efficient generation of random programs.
  */
object BinaryArraySpace {

  type CellAddress = Int

  type MemoryState = scala.collection.immutable.BitSet

  type MemoryStateBuffer = scala.collection.mutable.BitSet

  sealed abstract class Program {
  }

  object Program {
    def withStatements(statements: Seq[Statement]): Program.Simple = Program.Simple(statements:_*)

    case class Simple(statements: Statement*) extends Program
    case class Composite(subprograms: Program*) extends Program
    case object Empty extends Program
  }

  object ComputingSpace extends ComputingSpaceAPI[Program, MemoryState] {

    override def initialState: MemoryState = scala.collection.immutable.BitSet.empty

    override def compose(p1: Program, p2: Program): Program = Program.Composite(p1, p2)

    override def updateDigestWithMemState(ms: MemoryState, digest: CryptographicDigester): Unit = {
      for (element <- ms)
        digest.updateWith(element)
    }

    override def updateDigestWithProgram(program: Program, digest: CryptographicDigester): Unit = {
      digest.updateWith(0x01.toByte)
      program match {
        case p: Program.Simple => for (st <- p.statements) st.updateDigest(digest)
        case p: Program.Composite => for (sub <- p.subprograms) this.updateDigestWithProgram(sub, digest)
        case Program.Empty => digest.updateWith(0x03.toByte)
      }
      digest.updateWith(0x02.toByte)
    }

    override def execute(program: Program, memState: MemoryState, gasLimit: Gas): BinaryArraySpace.ComputingSpace.ProgramResult =
      program match {
        case p: Program.Simple => executeSimpleProgram(p, memState, gasLimit)
        case p: Program.Composite => executeCompositeProgram(p, memState, gasLimit)
        case Program.Empty => ProgramResult.Success(memState, 0)
      }

    private def executeSimpleProgram(program: Program.Simple, memState: MemoryState, gasLimit: Gas): ProgramResult = {
      val workingMemState: MemoryStateBuffer = scala.collection.mutable.BitSet.fromBitMask(memState.toBitMask)
      var gasBurned: Int = 0
      var accumulator: Int = 0
      var i: Int = 0
      var break: Boolean = false
      val programLength: Int = program.statements.length

      while (i < programLength && ! break) {
        if (gasBurned > gasLimit)
          return ProgramResult.GasLimitExceeded

        program.statements(i) match {
          case Statement.ClearAcc =>
            accumulator = 0
            i += 1
          case Statement.AddToAcc(address) =>
            accumulator = (accumulator + readBit(workingMemState, address)) % 2
            i += 1
          case Statement.StoreAcc(address) =>
            writeBit(workingMemState, address, accumulator)
            i += 1
          case Statement.Write(address, value) =>
            writeBit(workingMemState, address, value)
            i += 1
          case Statement.Flip(address) =>
            val b = readBit(workingMemState, address)
            val negated = if (b == 0) 1 else 0
            writeBit(workingMemState, address, negated)
            i += 1
          case Statement.Assert(address, value) =>
            if (readBit(workingMemState, address) != value)
              return ProgramResult.Crash(gasBurned)
            else
              i += 1
          case Statement.Branch(conditionAddress, goto) =>
            assert(goto > i,s"goto=$goto, i=$i")
            if (readBit(workingMemState, conditionAddress) == 1)
              i = goto
            else
              i += 1
          case Statement.Loop(goto) =>
            assert(goto < i,s"goto=$goto, i=$i")
            if (accumulator == 1)
              i = goto
            else
              i += 1
          case Statement.Nop =>
            i += 1
          case Statement.Exit =>
            break = true
        }

        gasBurned += 1
      }
      val finalMemoryState = scala.collection.immutable.BitSet.fromBitMaskNoCopy(workingMemState.toBitMask)
      return ProgramResult.Success(finalMemoryState, gasBurned)
    }

    private def executeCompositeProgram(program: Program.Composite, memState: MemoryState, gasLimit: Gas): ProgramResult = {
      var tmpMemState = memState
      var gasLimitLeft = gasLimit
      for (p <- program.subprograms) {
        this.execute(p, tmpMemState, gasLimitLeft) match {
          case ProgramResult.Success(ms, gasUsed) =>
            tmpMemState = ms
            gasLimitLeft -= gasUsed
          case r@ProgramResult.Crash(gasUsed) => return r
          case ProgramResult.GasLimitExceeded => return ProgramResult.GasLimitExceeded
        }
      }
      return ProgramResult.Success(tmpMemState, gasLimit - gasLimitLeft)
    }

    private def readBit(state: MemoryStateBuffer, address: Int): Int = if (state(address)) 1 else 0

    private def writeBit(state: MemoryStateBuffer, address: Int, value: Int): Unit = {
      value match {
        case 0 => state(address) = false
        case 1 => state(address) = true
        case other => throw new RuntimeException(s"updating bitset with illegal value $other (allowed are 0 and 1)")
      }
    }
  }

  sealed abstract class Statement {
    def updateDigest(digest: CryptographicDigester)
  }

  object Statement {
    //sets the accumulator to zero
    case object ClearAcc extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = digest.updateWith(StatementCode.clearAcc)
    }

    //reads memory at given address, adds to the accumulator (modulo 2) and stores the result the accumulator
    case class AddToAcc(address: CellAddress) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.addToAcc)
        digest.updateWith(address)
      }
    }

    //stores the value of 'accumulator' at the specified address
    case class StoreAcc(address: CellAddress) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.storeAcc)
        digest.updateWith(address)
      }
    }

    //writes the value 'value' at given address
    case class Write(address: CellAddress, value: Int) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.write)
        digest.updateWith(address)
        digest.updateWith(value)
      }
    }

    //adds 1 (modulo 2) to the value stored at given address
    case class Flip(address: CellAddress) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.flip)
        digest.updateWith(address)
      }
    }

    //reads the value at address; terminates the program abruptly if the value does not equal to 'value'
    case class Assert(address: CellAddress, value: Int) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.assert)
        digest.updateWith(address)
        digest.updateWith(value)
      }
    }

    //conditional jump forward: checks the value at address 'condition' - if 0 then program executes next instruction, if 1 then we skip specified number of instructions
    case class Branch(conditionAddress: CellAddress, goto: Int) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.branch)
        digest.updateWith(conditionAddress)
        digest.updateWith(goto)
      }
    }

    //conditional jump backwards: if accumulator contains 1 - we jump back specified number of instructions
    case class Loop(goto: Int) extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = {
        digest.updateWith(StatementCode.loop)
        digest.updateWith(goto)
      }
    }

    case object Nop extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = digest.updateWith(StatementCode.nop)
    }

    //graceful termination
    case object Exit extends Statement {
      override def updateDigest(digest: CryptographicDigester): Unit = digest.updateWith(StatementCode.exit)
    }
  }

  sealed abstract class StatementCode {
  }

  //needed for optimizing random program generation with optimized @switch
  //scala is so stupid that only when the final modifiers are here, the optimization of lookup in 'match' really happens
  object StatementCode {
    final val clearAcc = 1
    final val addToAcc = 2
    final val storeAcc = 3
    final val write = 4
    final val flip = 5
    final val assert = 6
    final val branch = 7
    final val loop = 8
    final val exit = 9
    final val nop = 10
  }

}
