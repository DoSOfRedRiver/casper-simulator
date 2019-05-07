package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorsBook.{BondingQueueAppendResult, BondingQueueItem, UnbondingQueueAppendResult, UnbondingQueueItem}

import scala.collection.immutable.Queue

/**
  * Immutable data structure we use to keep complete information about validators collection (as seen on-chain).
  * This is part of global state.
  * This data structure plays central role in:
  *    - distribution of block rewards
  *    - proof-of-stake implementation
  *
  * When comparing to the "real" blockchain, this data structure corresponds to the private storage of PoS blessed contract
  * (assuming that PoS logic is implemented as a blessed contract).
  *
  * @param validators current state of every validator
  * @param blocksWithActiveRewardsPayment queue of blocks that still have on-going black rewards cycle (when the blocks leaves this queue, all the remaining ether goes to the creator of the block)
  * @param blocksRewardEscrow per-block counters of still-to-be-consumed rewards
  * @param bondingQueue queue of bonding requests
  * @param unbondingQueue queue of unbonding requests
  * @param cachedNumberOfActiveValidators number of active validators (cached here for performance) - validator is active is his stake is non zero
  * @param cachedTotalStake total stake of validators (cached here for performance)
  * @param lastPTimeSeen last block time seen; we want to ensure that p-time is never going backwards (some logic depends on p-time being monotonic)
  */
class ValidatorsBook private (
                             private val validators: Map[ValidatorId, ValidatorState],
                             private val blocksWithActiveRewardsPayment: Queue[(Gas,BlockId, ValidatorId)],
                             private val blocksRewardEscrow: Map[BlockId, Ether],
                             private val bondingQueue: Queue[BondingQueueItem],
                             private val unbondingQueue: Queue[UnbondingQueueItem],
                             private val cachedNumberOfActiveValidators: Int,
                             private val cachedTotalStake: Ether,
                             private val lastPTimeSeen: Gas
                             ) {

  def addBondingReqToWaitingQueue(validator: ValidatorId, account: Account, amount: Ether, requestTime: Gas, config: BlockchainConfig): (ValidatorsBook, BondingQueueAppendResult) = {
    require(requestTime >= lastPTimeSeen)

    if (amount > config.maxBondingRequest)
      return (this, BondingQueueAppendResult.StakeChangeTooLarge)
    if (amount < config.minBondingUnbondingRequest)
      return (this, BondingQueueAppendResult.StakeChangeTooSmall)
    if (currentStakeOf(validator) == 0 && amount < config.minStake)
      return (this, BondingQueueAppendResult.EffectiveStakeBelowMin)
    val totalBondingAmountQueuedForThisValidator = bondingQueue.filter(item => item.validator == validator).map(item => item.amount).sum
    if (totalBondingAmountQueuedForThisValidator + amount > config.maxStake)
      return (this, BondingQueueAppendResult.EffectiveStakeAboveMax)

    //todo: add missing checks based on the "sliding window" principle

    val validatorState = validators.getOrElse(validator, ValidatorState.initial(validator, account, 0))

    val resultingBook = new ValidatorsBook(
      validators = validators + (validator -> validatorState.bondingEscrowLock(amount)),
      blocksWithActiveRewardsPayment,
      blocksRewardEscrow,
      bondingQueue.enqueue(BondingQueueItem(validator, amount, requestTime, requestTime + config.bondingDelay)),
      unbondingQueue,
      cachedNumberOfActiveValidators,
      cachedTotalStake,
      requestTime
    )

    return (resultingBook, BondingQueueAppendResult.OK)
  }

  def addUnbondingReqToWaitingQueue(validator: ValidatorId, amount: Ether, requestTime: Gas, config: BlockchainConfig): (ValidatorsBook, UnbondingQueueAppendResult) = {
    require(requestTime >= lastPTimeSeen)

    if (amount > config.maxUnbondingRequest)
      return (this, UnbondingQueueAppendResult.StakeChangeTooLarge)
    if (amount < config.minBondingUnbondingRequest)
      return (this, UnbondingQueueAppendResult.StakeChangeTooSmall)

    val currentStake: Ether = currentStakeOf(validator)
    if (currentStake == 0)
      return (this, UnbondingQueueAppendResult.ValidatorNotActive)

    val effectiveStake = currentStake - amount

    if (effectiveStake > 0 && effectiveStake < config.minStake)
      return (this, UnbondingQueueAppendResult.EffectiveStakeBelowMin)

    if (effectiveStake > config.maxStake)
      return (this, UnbondingQueueAppendResult.EffectiveStakeAboveMax)

    //todo: add missing checks based on the "sliding window" principle

    val validatorState = validators(validator)
    if (validatorState.bondingEscrow > 0)
      return (this, UnbondingQueueAppendResult.BondingRequestsCurrentlyQueued)

    val resultingBook = new ValidatorsBook(
      validators = validators + (validator -> validatorState.decreaseStake(amount)),
      blocksWithActiveRewardsPayment,
      blocksRewardEscrow,
      bondingQueue,
      unbondingQueue.enqueue(UnbondingQueueItem(validator, amount, requestTime, requestTime + config.unbondingDelay)),
      if (effectiveStake == 0) cachedNumberOfActiveValidators - 1 else cachedNumberOfActiveValidators,
      cachedTotalStake - amount,
      requestTime
    )

    return (resultingBook, UnbondingQueueAppendResult.OK)
  }

  /**
    * Here the actual increase of the stake happens.
    * All validators involved have their entries in validators map created before (this actually happens during adding of an entry in the bonding queue).
    *
    * @param pTime the blocktime of "now" - here is means that all queue entries with a triggering time less-or-equal than pTime will get executed now
    * @return updated ValidatorsBook (= with all the stakes increased, corresponding amounts released from escrow and bonding queue processed up to pTime)
    */
  def processBondingQueueUpToCurrentTime(pTime: Gas): ValidatorsBook = {
    require(pTime >= lastPTimeSeen)
    //finding requests ready to be processed
    val (readyToExecute, stillWaiting) = bondingQueue.span(item => item.triggeringTime < pTime)
    //total new stake
    val addedTotalStake = readyToExecute.map(item => item.amount).sum

    var validatorsMapTmp: Map[ValidatorId, ValidatorState] = validators
    for (item <- readyToExecute) {
      val oldValidatorState = validatorsMapTmp(item.validator)
      val newValidatorState = oldValidatorState.increaseStake(item.amount)
      validatorsMapTmp = validatorsMapTmp + (item.validator -> newValidatorState)
    }

    return new ValidatorsBook(
      validators = validatorsMapTmp,
      blocksWithActiveRewardsPayment,
      blocksRewardEscrow,
      bondingQueue = stillWaiting,
      unbondingQueue,
      cachedNumberOfActiveValidators = validatorsMapTmp count {case (v,s) => s.stake > 0},
      cachedTotalStake + addedTotalStake,
      pTime
    )
  }

  /**
    * Here only the ether trapped in the escrow (after unbonding request) is returned to the validator account.
    * Actually is it not returned, but we return a collection of ether transfers to be done (some upper layer must really process them).
    * Additionally we do some clean-up of validators map (some entries may be no longer needed).
    *
    * @param pTime the blocktime of "now" - here is means that all queue entries with a triggering time less-or-equal than pTime will get executed now
    * @return (updated validators book, ether transfers to be done)
    */
  def processUnbondingQueueUpToCurrentTime(pTime: Gas): (ValidatorsBook, Map[Account, Ether]) = {
    require(pTime >= lastPTimeSeen)
    //finding requests ready to be processed
    val (readyToExecute, stillWaiting) = unbondingQueue.span(item => item.triggeringTime < pTime)
    //compressing return transfers (so we will have one transfer per validator)
    val transfersMap: Map[ValidatorId, Ether] = readyToExecute.groupBy(item => item.validator) map { case (validator, requests) => (validator, requests.map(r => r.amount).sum) }
    //releasing values from the escrow
    val validatorsMapWithAllEscrowsProcessed: Map[ValidatorId, ValidatorState] = transfersMap.foldLeft(validators) { case (acc, (vid, ether)) =>
      val oldValidatorState = validators(vid)
      val updatedValidatorState = oldValidatorState.unbondingEscrowRelease(ether)
      acc + (vid -> updatedValidatorState)
    }
    //some validators are now obsolete and can be removed from the map
    //todo: fix removing of validators - we can remove a validator only after the queue of blocks with outstanding rewards does not contain any block created by this validator !
    //todo: other option would be to keep the account id directly in the queue (all we need is to be able to transfer outstanding rewards after the block is old enough)
    //caution: for now we just keep the validator forever
//    val validatorsToBeRemoved = transfersMap.keys.filter(vid => validatorsMapWithAllEscrowsProcessed(vid).isReadyToBeForgotten)

    val resultingBook = new ValidatorsBook(
      validators = validatorsMapWithAllEscrowsProcessed,
      blocksWithActiveRewardsPayment,
      blocksRewardEscrow,
      bondingQueue,
      unbondingQueue = stillWaiting,
      cachedNumberOfActiveValidators,
      cachedTotalStake,
      pTime
    )

    return (resultingBook, transfersMap map {case (vid, ether) => (validators(vid).account, ether)})
  }


  def currentStakeOf(validator: ValidatorId): Ether =
    validators.get(validator) match {
      case Some(state) => state.stake
      case None => 0
    }

  /**
    * Returns the state of specified validator.
    */
  def getInfoAbout(validator: ValidatorId): ValidatorState = validators(validator)

  def isRegistered(validator: ValidatorId): Boolean = validators.contains(validator)

  /**
    * Sum of stake that active validators have together.
    */
  def totalStake: Ether = cachedTotalStake

  /**
    * Returns collection of ids of active validators (= validators that have non-zero current stake)
    */
  def activeValidators: Iterable[ValidatorId] = (validators filter { case (id, state) => state.stake > 0 }).keys

  /**
    * Number of active validators (= validators that have non-zero current stake).
    */
  def numberOfValidators: Int = cachedNumberOfActiveValidators

  /**
    * Stores per-validator info on the reward for the given block.
    * Nothing is paid yet.
    *
    * Longer story: when a new block is created, the ether paid as transaction fees in this block is the total reward to be eventually paid
    * to validators. This total reward is distributed across active validators according to an algorithm that part of PoS semantics.
    */
  def distributeRewardsForJustCreatedBlock(blockId: BlockId, creator: ValidatorId, blockTime: Gas, totalReward: Ether): ValidatorsBook = {
    assert(blockTime >= lastPTimeSeen)
    assert(!blocksRewardEscrow.contains(blockId))

    val updatedMapOfValidators: Map[ValidatorId, ValidatorState] = validators map {
      case (id, state) =>
        if (state.stake == 0) (id, state)
        else (id, state.registerBlockRewardDue(blockId, totalReward * state.stake / totalStake, blockTime))
    }

    return new ValidatorsBook(
      validators = updatedMapOfValidators,
      blocksWithActiveRewardsPayment = blocksWithActiveRewardsPayment.enqueue((blockTime, blockId, creator)),
      blocksRewardEscrow = blocksRewardEscrow + (blockId -> totalReward),
      bondingQueue,
      unbondingQueue,
      cachedNumberOfActiveValidators,
      cachedTotalStake,
      blockTime)
  }

  /**
    * Checks the block rewards queue for blocks that are now too old to be claimed for rewards.
    * All unconsumed rewards still outstanding for these too-old blocks are to be paid to original creators of these blocks.
    *
    * @param blockTime block time we are doing cleanup against
    * @return
    */
  def cleanupBlockRewardsQueue(blockTime: Gas, timeLimitForClaimingBlockReward: Gas): (ValidatorsBook, Map[ValidatorId,Ether]) = {
    assert(blockTime >= lastPTimeSeen)

    val (oldBlocks, freshBlocks) = blocksWithActiveRewardsPayment span { case (ptime, blockId, creator) => ptime + timeLimitForClaimingBlockReward < blockTime }
    val account2ether: Map[Account, Ether] = (oldBlocks map {case (ptime, blockId, creator) => (getInfoAbout(creator).account, blocksRewardEscrow(blockId))}).toMap
    val blockIdsToBeRemoved = blocksWithActiveRewardsPayment map { case (ptime, blockId, creator) => blockId }

    val resultingValidatorsBook = new ValidatorsBook(
      validators,
      blocksWithActiveRewardsPayment = freshBlocks,
      blocksRewardEscrow -- blockIdsToBeRemoved,
      bondingQueue,
      unbondingQueue,
      cachedNumberOfActiveValidators,
      cachedTotalStake,
      blockTime)

    return (resultingValidatorsBook, account2ether)
  }

  /**
    * Calculates all the available block rewards for a given validator, assuming he just created a block with block-time=pTime.
    * His block rewards counter is reset to zero.
    *
    * @param validator validator that is about to consume his premium
    * @param pTime     block time of the block created by this validator
    * @return (updated validators book, ether to be paid as premium)
    */
  def consumeBlockRewards(validator: ValidatorId, pTime: Gas, timeLimitForClaimingBlockReward: Gas): (ValidatorsBook, Ether) = {
    assert(pTime >= lastPTimeSeen)

    val stuffToConsume: Queue[ValidatorState.UnconsumedBlockRewardInfo] =
      validators(validator).unconsumedBlockRewards.dropWhile(item => item.pTimeWhenEarned + timeLimitForClaimingBlockReward < pTime)
    val totalRewards: Ether = stuffToConsume.map(item => item.amount).sum
    val stateOfValidator: ValidatorState = validators(validator).resetUnconsumedBlockRewards
    val escrowUpdatesMap: Map[BlockId, Ether] = stuffToConsume.map(item => (item.blockId, item.amount)).toMap
    val updatedRewardsEscrow = blocksRewardEscrow map { case (blockId,ether) => (blockId, ether - escrowUpdatesMap.getOrElse(blockId, 0L))}

    val book: ValidatorsBook = new ValidatorsBook(
      validators = validators + (validator -> stateOfValidator),
      blocksWithActiveRewardsPayment,
      blocksRewardEscrow = updatedRewardsEscrow,
      bondingQueue,
      unbondingQueue,
      cachedNumberOfActiveValidators,
      cachedTotalStake,
      pTime)

    return (book, totalRewards)
  }

}

  //########################################################## PRIVATE ###########################################################

object ValidatorsBook {

  def empty = new ValidatorsBook(
    validators = Map.empty[ValidatorId, ValidatorState],
    blocksWithActiveRewardsPayment = Queue.empty,
    blocksRewardEscrow = Map.empty[BlockId, Ether],
    bondingQueue = Queue.empty,
    unbondingQueue = Queue.empty,
    cachedNumberOfActiveValidators = 0,
    cachedTotalStake = 0,
    lastPTimeSeen = 0
  )

  def genesis(foundersMap: Map[ValidatorId, ValidatorState]): ValidatorsBook = new ValidatorsBook(
    validators = foundersMap,
    blocksWithActiveRewardsPayment = Queue.empty,
    blocksRewardEscrow = Map.empty,
    bondingQueue = Queue.empty,
    unbondingQueue = Queue.empty,
    cachedNumberOfActiveValidators = foundersMap.values.count(s => s.stake > 0),
    cachedTotalStake = foundersMap.values.map(s => s.stake).sum,
    lastPTimeSeen = 0
  )

  case class BondingQueueItem(validator: ValidatorId, amount: Ether, requestTime: Gas, triggeringTime: Gas)

  case class UnbondingQueueItem(validator: ValidatorId, amount: Ether, requestTime: Gas, triggeringTime: Gas)

  sealed abstract class BondingQueueAppendResult

  object BondingQueueAppendResult {
    case object OK extends BondingQueueAppendResult
    case object StakeChangeTooSmall extends BondingQueueAppendResult
    case object StakeChangeTooLarge extends BondingQueueAppendResult
    case object EffectiveStakeBelowMin extends BondingQueueAppendResult
    case object EffectiveStakeAboveMax extends BondingQueueAppendResult
    case object NumberOfItemsInSlidingWindowExceeded extends BondingQueueAppendResult
    case object SumOfItemsInSlidingWindowExceeded extends BondingQueueAppendResult
    case object AccountMismatch extends BondingQueueAppendResult
  }

  sealed abstract class UnbondingQueueAppendResult

  object UnbondingQueueAppendResult {
    case object OK extends UnbondingQueueAppendResult
    case object StakeChangeTooSmall extends UnbondingQueueAppendResult
    case object StakeChangeTooLarge extends UnbondingQueueAppendResult
    case object EffectiveStakeBelowMin extends UnbondingQueueAppendResult
    case object EffectiveStakeAboveMax extends UnbondingQueueAppendResult
    case object NumberOfItemsInSlidingWindowExceeded extends UnbondingQueueAppendResult
    case object SumOfItemsInSlidingWindowExceeded extends UnbondingQueueAppendResult
    case object ValidatorNotActive extends UnbondingQueueAppendResult
    case object BondingRequestsCurrentlyQueued extends UnbondingQueueAppendResult
    case object AccountMismatch extends UnbondingQueueAppendResult
  }

}




