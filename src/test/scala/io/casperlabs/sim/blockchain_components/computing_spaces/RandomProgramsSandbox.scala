package io.casperlabs.sim.blockchain_components.computing_spaces

import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace.Statement._

object RandomProgramsSandbox {

  val cs = BinaryArraySpace.ComputingSpace

  val long1 = BinaryArraySpace.Program.Simple(
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

  val long2 = BinaryArraySpace.Program.Simple(
    StoreAcc(10),
    Write(38, 0),
    AddToAcc(98),
    Write(98, 1),
    Write(38, 0),
    Branch(155, 7),
    ClearAcc,
    StoreAcc(38),
    StoreAcc(155)
  )

  val long3 = BinaryArraySpace.Program.Simple(
    StoreAcc(180),
    ClearAcc,
    AddToAcc(38),
    AddToAcc(166),
    ClearAcc,
    Write(166, 1),
    Write(143, 1),
    ClearAcc,
    Write(180, 0),
    ClearAcc,
    ClearAcc,
    ClearAcc,
    Branch(180, 15),
    Assert(124, 0),
    StoreAcc(179),
    StoreAcc(106)
  )

  def main(args: Array[String]): Unit = {
    val executionResult = cs.execute(long3, cs.initialState, 200)
    println(s"result = $executionResult")
  }

}
