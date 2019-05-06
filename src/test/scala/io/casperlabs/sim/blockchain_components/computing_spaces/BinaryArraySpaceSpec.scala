package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.BaseSpec
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace.Statement._

import scala.collection.immutable.BitSet

class BinaryArraySpaceSpec extends BaseSpec {

  def extractMemoryStateIfPossible[P,MS](cs: ComputingSpace[P,MS])(r: cs.ProgramResult): Option[MS] = {
    r match {
      case cs.ProgramResult.Success(ms, gas) => Some(ms)
      case cs.ProgramResult.Crash(gas) => None
      case cs.ProgramResult.GasLimitExceeded => None
    }
  }

  trait CommonSetup {
    val empty: BinaryArraySpace.Program = BinaryArraySpace.Program.Empty
    val sample: BinaryArraySpace.MemoryState = BitSet(0, 2, 3, 7)
    val cs = BinaryArraySpace.ComputingSpace

    val copier = BinaryArraySpace.Program.Simple(
      AddToAcc(0),
      StoreAcc(1)
    )

    val eraser = BinaryArraySpace.Program.Simple(
      Write(0, 0),
      Write(1, 0),
      Write(2, 0),
      Write(3, 0),
      Write(4, 0),
      Write(5, 0),
      Write(6, 0),
      Write(7, 0)
    )

    val flipper = BinaryArraySpace.Program.Simple(
      Flip(0),
      Flip(1),
      Flip(2),
      Flip(3),
      Flip(4),
      Flip(5),
      Flip(6),
      Flip(7)
    )

    val crashing = BinaryArraySpace.Program.Simple(
      Assert(0, 0)
    )

    val branching = BinaryArraySpace.Program.Simple(
      AddToAcc(0),
      AddToAcc(1),
      AddToAcc(2),
      AddToAcc(3),
      AddToAcc(4),
      StoreAcc(5),
      Branch(5, 9),
      Write(6, 0),
      StoreAcc(0),
      Exit,
      Write(6, 1)
    )

    val looping = BinaryArraySpace.Program.Simple(
      Flip(137),
      AddToAcc(137),
      Branch(137,8),
      Flip(123),
      AddToAcc(3),
      AddToAcc(137),
      ClearAcc,
      Flip(3),
      Flip(123),
      AddToAcc(3),
      Loop(0)
    )

  }

  "binary array space" must "execute copying" in new CommonSetup {
    val res = cs.execute(copier, sample, 100)
    extractMemoryStateIfPossible(cs)(res).get shouldBe BitSet(0,1,2,3,7)
  }

  it must "execute erasing" in new CommonSetup {
    val res = cs.execute(eraser, sample, 100)
    extractMemoryStateIfPossible(cs)(res).get shouldBe BitSet()
  }

  it must "execute flipping" in new CommonSetup {
    val res = cs.execute(flipper, sample, 100)
    extractMemoryStateIfPossible(cs)(res).get shouldBe BitSet(1,4,5,6)
  }

  it must "handle crashing" in new CommonSetup {
    cs.execute(crashing, sample, 100) shouldBe a [cs.ProgramResult.Crash]
  }

  it must "execute sample program 1" in new CommonSetup {
    val res = cs.execute(branching, sample, 100)
    extractMemoryStateIfPossible(cs)(res).get shouldBe BitSet(0, 2, 3, 5, 7)
  }

  it must "execute sample program 2" in new CommonSetup {
    val res = cs.execute(looping, sample, 100)
    extractMemoryStateIfPossible(cs)(res).get shouldBe BitSet(0, 2, 3, 7, 123, 137)
  }

  it must "execute composition of programs" in new CommonSetup {
    val res1 = cs.execute(branching, sample, 100)
    val res2 = cs.execute(looping, extractMemoryStateIfPossible(cs)(res1).get ,100)
    val finalMemState1 = extractMemoryStateIfPossible(cs)(res2).get

    val composed = cs.compose(branching, looping)
    val res3 = cs.execute(composed, sample, 100)
    val finalMemState2 = extractMemoryStateIfPossible(cs)(res3).get

    finalMemState1 shouldEqual finalMemState2
  }

}

