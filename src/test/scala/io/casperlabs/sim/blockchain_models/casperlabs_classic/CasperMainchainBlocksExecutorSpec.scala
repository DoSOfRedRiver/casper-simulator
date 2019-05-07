package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.BaseSpec
import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace, MockingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.FakeHashGenerator

import scala.util.Random

class CasperMainchainBlocksExecutorSpec extends BaseSpec {

  type MS = MockingSpace.MemoryState
  type P = MockingSpace.Program
  type CS = ComputingSpace[P,MS]
  type GS = GlobalState[MS]

  //###################################### BLOCKCHAIN CONFIG ##################################

  val config: BlockchainConfig = new BlockchainConfig {

    val accountCreationCost: Gas = 10
    val transferCost: Gas = 2
    val successfulBondingCost: Gas = 20
    val refusedBondingCost: Gas = 2
    val successfulUnbondingCost: Gas = 20
    val refusedUnbondingCost: Gas = 2
    val slashingCost: Gas = 1

    val bondingDelay: Gas = 200
    val unbondingDelay: Gas = 100
    val maxStake: Ether = 1000
    val minStake: Ether = 5
    val minBondingUnbondingRequest: Ether = 50
    val maxBondingRequest: Ether = 500
    val maxUnbondingRequest: Ether = 2000
    val bondingSlidingWindowSize: Gas = 500
    val unbondingSlidingWindowSize: Gas = 500
    val bondingTrafficAsNumberOfRequestsLimit: Int = 5
    val bondingTrafficAsStakeDifferenceLimit: Ether =  500
    val unbondingTrafficAsNumberOfRequestsLimit: Int = 5
    val unbondingTrafficAsStakeDifferenceLimit: Ether = 800

    val pTimeLimitForClaimingBlockReward: Gas = 2000
  }

  //####################### EXECUTION ENGINE & BLOCKS EXECUTOR INIT #################################

  val computingSpace = MockingSpace.ComputingSpace
  val initialMemoryState: MS = MockingSpace.MemoryState.Singleton
  val ee = new DefaultExecutionEngine(config, computingSpace)
  val blocksExecutor = new CasperMainchainBlocksExecutor[CS,P,MS](ee, config)
  val random = new Random(42) //fixed seed, so tests are deterministic
  val blockIdGen = new FakeHashGenerator(random)
  val gasPrice: Ether = 1

  //#################################### GENESIS GLOBAL STATE #################################

  val account1 = 1
  val account2 = 2
  val account3 = 3
  val account4 = 4

  val validator1 = 101
  val validator2 = 102
  val validator3 = 103
  val validator4 = 104

  val accountsRegistry = new AccountsRegistry(
    Map (
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

  //#################################### TRANSACTIONS #################################

  val acc4_creation = Transaction.AccountCreation(
    nonce = 0,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    newAccount = account4
  )

  val acc1_to_acc4_transfer = Transaction.EtherTransfer(
    nonce = 1,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    targetAccount = account4,
    value = 70
  )

  val v1_bonding = Transaction.Bonding(
    nonce = 2,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    validator = validator1,
    value = 50
  )

  val v4_bonding = Transaction.Bonding(
    nonce = 0,
    sponsor = account4,
    gasPrice = 1,
    gasLimit = 50,
    validator4,
    value = 50
  )

  val acc1_smart_contract_happy = Transaction.SmartContractExecution(
    nonce = 3,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    MockingSpace.Program.Happy(200)
  )

  val acc1_smart_contract_happy2 = Transaction.SmartContractExecution(
    nonce = 4,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    MockingSpace.Program.Happy(1)
  )


  val v1_unbonding = Transaction.Unbonding(
    nonce = 5,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    validator = validator1,
    value = 550
  )


  val acc1_smart_contract_crashing = Transaction.SmartContractExecution(
    nonce = 6,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    MockingSpace.Program.Crashing(120)
  )

  val acc1_smart_contract_looping = Transaction.SmartContractExecution(
    nonce = 7,
    sponsor = account1,
    gasPrice = 1,
    gasLimit = 200,
    MockingSpace.Program.Looping
  )

  //#################################### BLOCKS #################################

  val genesis = Genesis.generate("casperlabs", ee.globalStateHash(initialGlobalState))

  def makeBlock(parent: Block, postStateOfParent: GlobalState[MS], tx: Transaction): (Block, GlobalState[MS]) = {
    val pTimeOfThisBlock = parent.pTime + parent.gasBurned
    val newBlockId = blocksExecutor.calculateBlockId(validator1, IndexedSeq(parent.id), IndexedSeq(parent.id), IndexedSeq(tx), parent.postStateHash)
    val (postState, gasBurned) = blocksExecutor.executeBlockAsCreator(postStateOfParent, pTimeOfThisBlock, newBlockId, validator1, IndexedSeq(tx))

    val block = NormalBlock(
      id = newBlockId,
      creator = validator1,
      dagLevel = parent.dagLevel + 1,
      parents = IndexedSeq(parent),
      justifications = IndexedSeq(parent),
      transactions = IndexedSeq(tx),
      pTime = parent.pTime + parent.gasBurned,
      gasBurned = gasBurned,
      preStateHash = ee.globalStateHash(postStateOfParent),
      postStateHash = ee.globalStateHash(postState)
    )

    return (block, postState)
  }

  trait CommonSetup {
    //we are building a simple blockdag here, which is actually a chain (every block has only one parent)

    val (b1, gsAfterB1) = makeBlock(genesis, initialGlobalState, acc4_creation)
    val (b2, gsAfterB2) = makeBlock(b1, gsAfterB1, acc1_to_acc4_transfer)
    val (b3, gsAfterB3) = makeBlock(b2, gsAfterB2, v1_bonding)
    val (b4, gsAfterB4) = makeBlock(b3, gsAfterB3, v4_bonding)
    val (b5, gsAfterB5) = makeBlock(b4, gsAfterB4, acc1_smart_contract_happy)
    val (b6, gsAfterB6) = makeBlock(b5, gsAfterB5, acc1_smart_contract_happy2)
    val (b7, gsAfterB7) = makeBlock(b6, gsAfterB6, v1_unbonding)
    val (b8, gsAfterB8) = makeBlock(b7, gsAfterB7, acc1_smart_contract_crashing)
    val (b9, gsAfterB9) = makeBlock(b8, gsAfterB8, acc1_smart_contract_looping)

  }

  "casper mainchain blocks executor" must "pass the bonding-unbonding sequence with interlacing requests" in new CommonSetup {
    initialGlobalState.numberOfActiveValidators shouldBe 2
    initialGlobalState.accountBalance(account1) shouldBe 1000L
    initialGlobalState.validatorsBook.currentStakeOf(validator1) shouldBe 500L

    gsAfterB2.accountBalance(account4) shouldBe 70L

    val v1AfterB4 = gsAfterB4.validatorsBook.getInfoAbout(validator1)
    v1AfterB4.stake shouldBe 500L
    v1AfterB4.bondingEscrow shouldBe 50L

    gsAfterB5.numberOfActiveValidators shouldBe 2

    gsAfterB6.numberOfActiveValidators shouldBe 3
    val v1AfterB6 = gsAfterB6.validatorsBook.getInfoAbout(validator1)
    v1AfterB6.stake shouldBe 550L
    v1AfterB6.bondingEscrow shouldBe 0L
    val v4AfterB6 = gsAfterB6.validatorsBook.getInfoAbout(validator4)
    v4AfterB6.stake shouldBe 50L

    gsAfterB7.numberOfActiveValidators shouldBe 2
    val v1AfterB7 = gsAfterB7.validatorsBook.getInfoAbout(validator1)
    v1AfterB7.stake shouldBe 0L
    v1AfterB7.unbondingEscrow shouldBe 550L


    gsAfterB9.numberOfActiveValidators shouldBe 2
    val v1AfterB9 = gsAfterB9.validatorsBook.getInfoAbout(validator1)
    v1AfterB9.stake shouldBe 0L
    v1AfterB9.unbondingEscrow shouldBe 0L
  }

  it must "correctly calculate block rewards" in {
    //todo: add proper assertions
    val (b1, gsAfterB1) = makeBlock(genesis, initialGlobalState, acc4_creation)
    val (b2, gsAfterB2) = makeBlock(b1, gsAfterB1, acc1_to_acc4_transfer)
    val (b3, gsAfterB3) = makeBlock(b2, gsAfterB2, v1_bonding)
    val (b4, gsAfterB4) = makeBlock(b3, gsAfterB3, v4_bonding)
    val (b5, gsAfterB5) = makeBlock(b4, gsAfterB4, acc1_smart_contract_happy)
    val (b6, gsAfterB6) = makeBlock(b5, gsAfterB5, acc1_smart_contract_happy2)
    val (b7, gsAfterB7) = makeBlock(b6, gsAfterB6, v1_unbonding)
    val (b8, gsAfterB8) = makeBlock(b7, gsAfterB7, acc1_smart_contract_crashing)
    val (b9, gsAfterB9) = makeBlock(b8, gsAfterB8, acc1_smart_contract_looping)
  }
}