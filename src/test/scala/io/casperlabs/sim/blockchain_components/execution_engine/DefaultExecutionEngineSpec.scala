package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.BaseSpec
import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.MockingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.hashing.FakeHashGenerator

import scala.util.Random

class DefaultExecutionEngineSpec extends BaseSpec {

  //###################################### BLOCKCHAIN CONFIG ##################################

  val config = new BlockchainConfig {

    val accountCreationCost: Gas = 10
    val transferCost: Gas = 2
    val successfulBondingCost: Gas = 20
    val refusedBondingCost: Gas = 2
    val successfulUnbondingCost: Gas = 20
    val refusedUnbondingCost: Gas = 2
    val slashingCost: Gas = 1

    val bondingDelay: Gas = 200
    val unbondingDelay: Gas = 200
    val maxStake: Ether = 1000
    val minStake: Ether = 5
    val minBondingUnbondingRequest: Ether = 50
    val maxBondingRequest: Ether = 500
    val maxUnbondingRequest: Ether = 500
    val bondingSlidingWindowSize: Gas = 500
    val unbondingSlidingWindowSize: Gas = 500
    val bondingTrafficAsNumberOfRequestsLimit: Int = 5
    val bondingTrafficAsStakeDifferenceLimit: Ether = 500
    val unbondingTrafficAsNumberOfRequestsLimit: Int = 5
    val unbondingTrafficAsStakeDifferenceLimit: Ether = 800

    val pTimeLimitForClaimingBlockReward: Gas = 2000
  }

  //#################################### EXECUTION ENGINE INIT #################################

  val computingSpace = MockingSpace.ComputingSpace
  val initialMemoryState: MockingSpace.MemoryState = MockingSpace.MemoryState.Singleton
  val ee = new DefaultExecutionEngine(config, computingSpace)
  val random = new Random(42) //fixed seed, so tests are deterministic
  val hashGen = new FakeHashGenerator(random)

  //#################################### GLOBAL STATE INIT #################################

  val account1 = 1
  val account2 = 2
  val account3 = 3
  val account4 = 4

  val validator1 = 101
  val validator2 = 102
  val validator3 = 103

  val accountsRegistry = new AccountsRegistry(
    Map(
      account1 -> AccountState(nonce = 0, balance = 1000L),
      account2 -> AccountState(nonce = 0, balance = 0L),
      account3 -> AccountState(nonce = 0, balance = 0L),
    )
  )

  val validatorsBook = ValidatorsBook.genesis(
    Map(
      validator1 -> ValidatorState.initial(validator1, account1, 500),
      validator2 -> ValidatorState.initial(validator2, account2, 200),
      validator3 -> ValidatorState.initial(validator2, account3, 0)
    )
  )

  val initialGlobalState = GlobalState(initialMemoryState, accountsRegistry, validatorsBook)

  //#################################### TRANSACTIONS ##################################

  "execution engine" must "execute account creation and sending some tokens to it" in {
    val tx1 = Transaction.AccountCreation(
      nonce = 0,
      sponsor = account1,
      gasPrice = 1,
      gasLimit = 200,
      newAccount = account4
    )
    val tx2 = Transaction.EtherTransfer(
      nonce = 1,
      sponsor = account1,
      gasPrice = 1,
      gasLimit = 200,
      targetAccount = account4,
      value = 50
    )

    val (gs1, txResult1) = ee.executeTransaction(initialGlobalState, tx1, effectiveGasPrice = 1, blockTime = 0)
    txResult1 shouldBe a[TransactionExecutionResult.Success]

    val (gs2, txResult2) = ee.executeTransaction(gs1, tx2, effectiveGasPrice = 1, blockTime = 0)
    txResult2 shouldBe a[TransactionExecutionResult.Success]

    gs2.accountBalance(account4) shouldEqual 50L
  }

  it must "handle successful smart contract execution" in {
    //todo
  }

  it must "handle crashing smart contract execution" in {
    //todo
  }

  it must "handle gas exceeded error in  smart contract execution" in {
    //todo
  }

  it must "discover ether insufficient for covering gas limit" in {
    //todo
  }

  it must "discover ether insufficient for doing too large transfer" in {
    //todo
  }

  it must "discover ether insufficient for covering gas" in {
    //todo
  }

}