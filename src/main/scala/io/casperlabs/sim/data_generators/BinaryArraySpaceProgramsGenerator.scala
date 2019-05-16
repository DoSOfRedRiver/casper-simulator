package io.casperlabs.sim.data_generators

import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace._
import io.casperlabs.sim.blockchain_components.execution_engine.Gas
import io.casperlabs.sim.statistics.{GaussDistributionGenerator, GaussDistributionParams}

import scala.annotation.switch
import scala.util.Random

/**
  * Generator of random programs in BinaryArraySpace.
  *
  * Length of resulting programs has Gauss distribution with parameters (averageLength, standardDeviation).
  * Cell addresses are picked from [0 ... memorySize - 1] interval.
  *
  * We try to make generated programs more "realistic" by keeping the collection of addresses each program uses limited.
  * To achieve this we generate random pool of addresses first, and then the generated program picks addresses only
  * from the pool. The pool's size is calculated as: program.length * entanglementFactor.
  * Low values of entanglementFactor mean that programs will use less addresses (so statements will tend to be more
  * inter-dependent). Reasonable values of entanglementFactor are in [0.1 .... 2].
  *
  * @param random source of randomness
  * @param averageLength average length of a program (as number of statements)
  * @param standardDeviation standard deviation of program length
  * @param memorySize cell addresses in resulting programs picked from [0 ... memorySize - 1] interval
  * @param statementsFrequencyTable map of relative frequencies of statements (frequencies do not have to be normalized)
  * @param entanglementFactor cellPool to program length ratio
  */
class BinaryArraySpaceProgramsGenerator(
                                         random: Random,
                                         programSizeRange: GaussDistributionParams,
                                         memorySize: Int,
                                         statementsFrequencyTable: Map[Int,Double],
                                         entanglementFactor: Double,
                                         gasLimitToProgramSizeFactor: Double,
                                       ) extends ProgramsGenerator[BinaryArraySpace.Program, BinaryArraySpace.MemoryState, BinaryArraySpace.ComputingSpace.type] {

  private val statementSelector: RandomSelector[Int] = new RandomSelector(random, statementsFrequencyTable)
  private val programSizeGenerator = new GaussDistributionGenerator(random, programSizeRange)

  def next(): (Program.Simple, Gas) = {
    val programLength = nextRandomProgramLength()
    val cellPoolSize: Int = math.max(1, (programLength * entanglementFactor).toInt)
    val cellPool = new Array[Int](cellPoolSize)
    //using low level loop below as performance optimization
    for (i <- cellPool.indices)
      cellPool(i) = random.nextInt(memorySize)

    val statements = for {
      i <- 0 until programLength
      s: Int = statementSelector.next()
    }
    yield
      (s: @switch) match {
        case StatementCode.addToAcc => Statement.AddToAcc(randomAddress(cellPool))
        case StatementCode.assert => Statement.Assert(randomAddress(cellPool), randomBit)
        case StatementCode.branch =>
          if (i <= programLength - 3)
            Statement.Branch(randomAddress(cellPool), randomFromInterval(i + 2, programLength - 1))
          else
            Statement.Nop
        case StatementCode.loop =>
          if (i <= 2)
            Statement.Nop
          else
            Statement.Loop(randomFromInterval(0, i - 3))
        case StatementCode.clearAcc => Statement.ClearAcc
        case StatementCode.exit => Statement.Exit
        case StatementCode.flip => Statement.Flip(randomAddress(cellPool))
        case StatementCode.storeAcc => Statement.StoreAcc(randomAddress(cellPool))
        case StatementCode.write => Statement.Write(randomAddress(cellPool), randomBit)
        case StatementCode.nop => Statement.Nop
      }

    val gasLimit: Gas = (programLength * gasLimitToProgramSizeFactor).toLong
    return (Program.withStatements(statements), gasLimit)
  }

  private def nextRandomProgramLength(): Int = {
    val g: Double = programSizeGenerator.next()
    return math.max(g.toInt, 1)
  }

  private def randomAddress(cellPool: Array[CellAddress]): CellAddress = cellPool(random.nextInt(cellPool.length))

  private def randomBit: Int = if (random.nextBoolean()) 1 else 0

  private def randomFromInterval(from: Int, to: Int): Int = random.nextInt(to - from + 1) + from

}

