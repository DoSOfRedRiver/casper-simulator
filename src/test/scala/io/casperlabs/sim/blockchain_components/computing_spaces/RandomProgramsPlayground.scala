package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.data_generators.BinaryArraySpaceProgramsGenerator
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace.StatementCode
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace._

import scala.util.Random

object RandomProgramsPlayground {

  val cs = BinaryArraySpace.ComputingSpace

  val gen = new BinaryArraySpaceProgramsGenerator(
    random = new Random,
    averageLength = 20,
    standardDeviation = 10,
    memorySize = 200,
    frequenciesOfStatements = Map(
      StatementCode.addToAcc -> 1.5,
      StatementCode.assert -> 0.002,
      StatementCode.branch -> 0.3,
      StatementCode.loop -> 0.05,
      StatementCode.clearAcc -> 1,
      StatementCode.exit -> 0.01,
      StatementCode.flip -> 1,
      StatementCode.nop -> 0.01,
      StatementCode.storeAcc -> 1,
      StatementCode.write -> 1,
    ),
    entanglementFactor = 0.5
  )

  def main(args: Array[String]): Unit = {
    checkExecutionStatistics(10000, false)
//    findNicePrograms(100000)
//    viewAndExecuteSamplePrograms(20)
  }

  def checkExecutionStatistics(n: Int, dumpBigCasCases: Boolean): Unit = {
    var gasLimitExceededCount: Int = 0
    var crashCount: Int = 0
    var gasBiggerThanProgramLengthCount: Int = 0
    var successCount: Int = 0

    for (i <- 0 to n) {
      val program = gen.next()
      val executionResult = cs.execute(program, cs.initialState, 200)
      executionResult match {
        case ComputingSpace.ProgramResult.Success(ms, gasUsed) =>
          successCount += 1
          if (gasUsed > program.statements.length) {
            gasBiggerThanProgramLengthCount += 1
            if (dumpBigCasCases) {
              println(s"gas used = $gasUsed")
              dumpSourceCode(program)
              println("----------------------------")
            }
          }
        case ComputingSpace.ProgramResult.Crash(gasUsed) =>
          crashCount += 1
        case ComputingSpace.ProgramResult.GasLimitExceeded =>
          gasLimitExceededCount += 1
      }
    }

    println("==========================================================================")
    println(s"success: $successCount")
    println(s"gas limit exceeded: $gasLimitExceededCount")
    println(s"crash: $crashCount")
    println(s"gas bigger than program length: $gasBiggerThanProgramLengthCount")
  }

  def findNicePrograms(n: Int): Unit = {
    for (i <- 0 to n) {
      val program = gen.next()
      val executionResult = cs.execute(program, cs.initialState, 500)
      executionResult match {
        case ComputingSpace.ProgramResult.Success(ms, gasUsed) =>
          if (program.statements.length < 50 && gasUsed > program.statements.length * 3) {
            println(s"gas used = $gasUsed")
            dumpSourceCode(program)
            println("----------------------------")
          }
        case ComputingSpace.ProgramResult.Crash(gasUsed) => //ignore
        case ComputingSpace.ProgramResult.GasLimitExceeded => //ignore
      }
      if (i % 500 == 0)
        println(s"checked $i programs")
    }
  }

  def viewAndExecuteSamplePrograms(n: Int): Unit = {
    for (i <- 0 to n) {
      val program = gen.next()
      dumpSourceCode(program)
      println("------------------------------------")
      val executionResult = cs.execute(program, cs.initialState, 200)
      println(s"result = $executionResult")
      println("====================================")
    }
  }

  def dumpSourceCode(p: BinaryArraySpace.Program.Simple): Unit = {
    for ((statement, i) <- p.statements.zipWithIndex)
      println(s"$i: $statement")
  }

}
