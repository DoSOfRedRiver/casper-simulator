package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace => ComputingSpaceAPI}
import io.casperlabs.sim.blockchain_components.execution_engine.Gas
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

/**
  * Computing space where memory states are elements of {0,1, ..., n-1}.
  * Programs are permutations.
  */
object FinitePermutationsSpace {

  type MemoryState = Int

  //A permutation is a bijection [0...n-1] --> [0...n-1].
  //We represent permutations of n-element set [0...n-1] as int arrays of size n.
  //Array can be seen as set of pairs (key,value), where key is the index. Then, being a set of pairs, the array represents a function.
  //For example array [2,1,0,3] represents permutation {(0,2), (1,1), (2,0), (3,3)}.
  case class Program(permutation: Array[Int])

  class ComputingSpace(n: Int) extends ComputingSpaceAPI[Program, MemoryState] {
    override def initialState: MemoryState = 0

    override def compose(p1: Program, p2: Program): Program = {
      val result = new Array[Int](p1.permutation.length)
      for (i <- 0 to result.length)
        result(i) = p2.permutation(p1.permutation(i))
      return Program(result)
    }

    override def execute(program: Program, memState: MemoryState, gasLimit: Gas): ProgramResult = ProgramResult.Success(program.permutation(memState), 1)

    override def updateDigestWithMemState(ms: MemoryState, digest: CryptographicDigester): Unit = digest.updateWith(ms)

    override def updateDigestWithProgram(p: Program, digest: CryptographicDigester): Unit = for (x <- p.permutation) digest.updateWith(x)
  }

}
