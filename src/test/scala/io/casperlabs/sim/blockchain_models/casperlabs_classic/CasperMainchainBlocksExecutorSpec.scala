package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.BaseSpec
import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace, MockingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorState.UnconsumedBlockRewardInfo
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorsBook.ActiveRewardsPaymentQueueItem
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.FakeHashGenerator
import org.scalatest.enablers.Containing._

import scala.collection.immutable.Queue
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

    val pTimeLimitForClaimingBlockReward: Gas = 300
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
  val account5 = 5

  val validator1 = 101 //bonded since genesis; making the initial sequence of blocks
  val validator2 = 102 //bonded since genesis; testing accumulating and consuming rewards
  val validator3 = 103 //bonded since genesis but with zero stake; should be recognized as "inactive" and not break the logic
  val validator4 = 104 //new validator that attempts to join the club
  val validator5 = 105 //bonded since genesis; never making a block and so at some point missing his rewards

  val accountsRegistry = new AccountsRegistry(
    Map (
      account1 -> AccountState(nonce = 0, balance = 1000L),
      account2 -> AccountState(nonce = 0, balance = 0L),
      account3 -> AccountState(nonce = 0, balance = 0L),
      account5 -> AccountState(nonce = 0, balance = 0L)
    )
  )

  val validatorsBook = ValidatorsBook.genesis(
    Map(
      validator1 -> ValidatorState.initial(validator1, account1, 500),
      validator2 -> ValidatorState.initial(validator2, account2, 200),
      validator3 -> ValidatorState.initial(validator3, account3, 0),
      validator5 -> ValidatorState.initial(validator5, account5, 100)
    )
  )

  val genesisGlobalState = GlobalState(initialMemoryState, accountsRegistry, validatorsBook)

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

  val genesis = Genesis.generate("casperlabs", ee.globalStateHash(genesisGlobalState))

  def makeBlock(creator: ValidatorId, parent: Block, postStateOfParent: GlobalState[MS], tx: Transaction): (Block, GlobalState[MS]) = {
    val pTimeOfThisBlock = parent.pTime + parent.gasBurned
    val newBlockId = blocksExecutor.calculateBlockId(validator1, IndexedSeq(parent.id), IndexedSeq(parent.id), IndexedSeq(tx), parent.postStateHash)
    val (postState, gasBurned) = blocksExecutor.executeBlockAsCreator(postStateOfParent, pTimeOfThisBlock, newBlockId, creator, IndexedSeq(tx))

    val block = NormalBlock(
      id = newBlockId,
      creator,
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
    //the blockchain is complex enough to activate all corner cases in the processing

    val (block1, gsAfterB1) = makeBlock(validator1, genesis, genesisGlobalState, acc4_creation)
    val (block2, gsAfterB2) = makeBlock(validator1, block1, gsAfterB1, acc1_to_acc4_transfer)
    val (block3, gsAfterB3) = makeBlock(validator1, block2, gsAfterB2, v1_bonding)
    val (block4, gsAfterB4) = makeBlock(validator2, block3, gsAfterB3, v4_bonding)
    val (block5, gsAfterB5) = makeBlock(validator1, block4, gsAfterB4, acc1_smart_contract_happy)
    val (block6, gsAfterB6) = makeBlock(validator1, block5, gsAfterB5, acc1_smart_contract_happy2)
    val (block7, gsAfterB7) = makeBlock(validator4, block6, gsAfterB6, v1_unbonding)
    val (block8, gsAfterB8) = makeBlock(validator1, block7, gsAfterB7, acc1_smart_contract_crashing)
    val (block9, gsAfterB9) = makeBlock(validator1, block8, gsAfterB8, acc1_smart_contract_looping)
  }

  "casper mainchain blocks executor" must "pass the bonding-unbonding sequence with interlacing requests" in new CommonSetup {
    genesisGlobalState.numberOfActiveValidators shouldBe 3
    genesisGlobalState.accountBalance(account1) shouldBe 1000L
    genesisGlobalState.validatorsBook.currentStakeOf(validator1) shouldBe 500L

    gsAfterB2.accountBalance(account4) shouldBe 70L

    gsAfterB3.validatorsBook.bondingQueue should contain (ValidatorsBook.BondingQueueItem(validator1, 50L, block3.pTime, block3.pTime + config.bondingDelay))

    val v1AfterB4 = gsAfterB4.validatorsBook.getInfoAbout(validator1)
    v1AfterB4.stake shouldBe 500L
    v1AfterB4.bondingEscrow shouldBe 50L

    gsAfterB5.numberOfActiveValidators shouldBe 3

    gsAfterB6.numberOfActiveValidators shouldBe 4
    val v1AfterB6 = gsAfterB6.validatorsBook.getInfoAbout(validator1)
    v1AfterB6.stake shouldBe 550L
    v1AfterB6.bondingEscrow shouldBe 0L
    val v4AfterB6 = gsAfterB6.validatorsBook.getInfoAbout(validator4)
    v4AfterB6.stake shouldBe 50L

    gsAfterB7.numberOfActiveValidators shouldBe 3
    val v1AfterB7 = gsAfterB7.validatorsBook.getInfoAbout(validator1)
    v1AfterB7.stake shouldBe 0L
    v1AfterB7.unbondingEscrow shouldBe 550L


    gsAfterB9.numberOfActiveValidators shouldBe 3
    val v1AfterB9 = gsAfterB9.validatorsBook.getInfoAbout(validator1)
    v1AfterB9.stake shouldBe 0L
    v1AfterB9.unbondingEscrow shouldBe 0L
  }

  it must "correctly calculate block rewards" in new CommonSetup {
    genesisGlobalState.validatorsBook.blocksWithActiveRewardsPayment shouldBe Queue.empty
    genesisGlobalState.validatorsBook.getInfoAbout(validator1).unconsumedBlockRewards shouldBe empty
    gsAfterB1.validatorsBook.blocksWithActiveRewardsPayment.toList shouldEqual List(ActiveRewardsPaymentQueueItem(block1.pTime, block1.id, validator1))
    val amountEarned: Ether = block1.gasBurned * gsAfterB1.validatorsBook.getInfoAbout(validator2).stake / gsAfterB1.validatorsBook.totalStake
    gsAfterB1.validatorsBook.getInfoAbout(validator2).unconsumedBlockRewards.toList shouldEqual List(
      UnconsumedBlockRewardInfo(block1.pTime, block1.id, amountEarned)
    )
  }

  it must "consume outstanding block rewards after a validator builds a new block" in new CommonSetup {
    val v2stake: Ether = genesisGlobalState.validatorsBook.getInfoAbout(validator2).stake
    val totalStake: Ether = genesisGlobalState.validatorsBook.totalStake
    val amountEarnedForB1: Ether = block1.gasBurned * v2stake / totalStake
    val amountEarnedForB2: Ether = block2.gasBurned * v2stake / totalStake //turns out to be zero, so not getting included in the queue
    val amountEarnedForB3: Ether = block3.gasBurned * v2stake / totalStake
    val amountEarnedForB4: Ether = block4.gasBurned * v2stake / totalStake

    gsAfterB3.validatorsBook.getInfoAbout(validator2).unconsumedBlockRewards.toList shouldEqual List(
      UnconsumedBlockRewardInfo(block1.pTime, block1.id, amountEarnedForB1),
      UnconsumedBlockRewardInfo(block3.pTime, block3.id, amountEarnedForB3)
    )

    gsAfterB4.validatorsBook.getInfoAbout(validator2).unconsumedBlockRewards shouldEqual Queue.empty
    val rewardTransferredAfterB4ToValidator2Account: Ether = gsAfterB4.accountBalance(account2) - gsAfterB3.accountBalance(account2)
    rewardTransferredAfterB4ToValidator2Account shouldEqual (amountEarnedForB1 + amountEarnedForB2 + amountEarnedForB3 + amountEarnedForB4)
  }

  it must "pay the unconsumed reward back to block creator after <claiming p-time limit> is achieved" in new CommonSetup {
    val totalStake: Ether = gsAfterB4.validatorsBook.totalStake
    val v1stake: Ether = gsAfterB4.validatorsBook.getInfoAbout(validator1).stake
    val v2stake: Ether = gsAfterB4.validatorsBook.getInfoAbout(validator2).stake
    val v5stake: Ether = gsAfterB4.validatorsBook.getInfoAbout(validator5).stake

    val amountThatV1EarnedForB4: Ether = block4.gasBurned * v1stake / totalStake
    val amountThatV2EarnedForB4: Ether = block4.gasBurned * v2stake / totalStake
    val amountThatV5EarnedForB4: Ether = block4.gasBurned * v5stake / totalStake // this reward is never consumed by validator 5, so it is eventually paid back to block's creator

    val totalRewardForB4 = block4.gasBurned * gasPrice
    val rewardToBeReturnedToV2 = totalRewardForB4 - (amountThatV1EarnedForB4 + amountThatV2EarnedForB4)

    val validator2accountDifferenceObserverOnProcessingOfBlockB9 = gsAfterB9.accountBalance(account2) - gsAfterB8.accountBalance(account2)
    validator2accountDifferenceObserverOnProcessingOfBlockB9 shouldEqual rewardToBeReturnedToV2
  }

  it must "cleanup the rewards queue according to p-time" in new CommonSetup {
    gsAfterB8.validatorsBook.blocksWithActiveRewardsPayment.contains(ActiveRewardsPaymentQueueItem(32, block4.id, validator2)) shouldBe true
    gsAfterB9.validatorsBook.blocksWithActiveRewardsPayment.contains(ActiveRewardsPaymentQueueItem(32, block4.id, validator2)) shouldBe false
  }

}