package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorsBook.BondingQueueAppendResult

/**
  * Immutable data structure we use to keep complete information about validators collection (as seen on-chain).
  * This is part of global state.
  *
  * Caution: this structure is very likely to evolve soon, so we keep very strict encapsulation policy here.
  */
class ValidatorsBook private (
                               private val validators: Map[ValidatorId, ValidatorState],
                               private val bondingBuffer: Seq[BondingQueueItem],
                               private val unbondingBuffer: Seq[UnbondingQueueItem],
                               private val cachedNumberOfActiveValidators: Int,
                               private val cachedTotalStake: Ether
                             ) {

  def addBondingReqToWaitingQueue(validator: ValidatorId, amount: Ether, requestTime: Gas, config: BlockchainConfig): (ValidatorsBook, BondingQueueAppendResult) = ??? //todo

  def addUnbondingReqToWaitingQueue(validator: ValidatorId, amount: Ether, requestTime: Gas, config: BlockchainConfig): (ValidatorsBook, BondingQueueAppendResult) = ??? //todo

  /**
    * Creates new validators book with bonding/unbonding queue processed up to the specified timepoint.
    *
    * Caution: there is certain lack of symmetry between bonding and unbonding. While unbonding, we have to move tokens back
    * to validator's account - this is the reason why we return a map validator-id ----> ether amount.
    * These transfers must be processed at another layer of the logic.
    *
    * @param pTime block-time point
    * @return (new validators book, map of unbonding transfers to be made)
    */
  def processQueueAccordingToCurrentTime(pTime: Gas): (ValidatorsBook, Map[ValidatorId, Ether]) = ??? //todo

  def currentStakeOf(validator: ValidatorId): Ether =
    validators.get(validator) match {
      case Some(state) => state.stake
      case None => 0
    }

  /**
    * Returns the state of specified validator.
    */
  def getInfoAbout(validator: ValidatorId): ValidatorState = validators(validator)

  /**
    * Sum of stake that active validators have together.
    */
  def totalStake: Ether = cachedTotalStake

  /**
    * Returns collection of ids of active validators (= validators that have non-zero current stake)
    */
  def activeValidators: Iterable[ValidatorId] = (validators filter {case (id,state) => state.stake > 0}).keys

  /**
    * Number of active validators (= validators that have non-zero current stake).
    */
  def numberOfValidators: Int = cachedNumberOfActiveValidators

  /**
    * Stores block reward due.
    *
    * Longer story: when a new block is created, the ether paid as transaction fees in this block is the total reward to be eventually paid
    * to validators. This total reward is distributed across active validators according to an algorithm that part of PoS semantics.
    *
    * This method stores the information about transaction fees paid.
    *
    * @param amount amount earned per-validator
    * @param pTime pTime of the block from which the reward comes
    * @return updated ValidatorsBook
    */
  def storeBlockReward(amount: Ether, pTime: Gas): ValidatorsBook = {
    val updatedMapOfValidators: Map[ValidatorId, ValidatorState] = validators map {
      case (id,state) =>
        if (state.stake == 0) (id,state)
        else (id, state.storeBlockReward(amount, pTime))
    }

    return new ValidatorsBook(updatedMapOfValidators, bondingBuffer, unbondingBuffer, cachedNumberOfActiveValidators, cachedTotalStake)
  }

  /**
    * Calculates all the available block rewards for a given validator, assuming he just created a block with block-time=pTime.
    * His block rewards counter is reset to zero.
    *
    * @param validator validator that is about to consume his premium
    * @param pTime block time of the block created by this validator
    * @return (updated validators book, ether to be paid as premium)
    */
  def consumeBlockRewards(validator: ValidatorId, pTime: Gas): (ValidatorsBook, Ether) = {
    val totalRewards: Ether = validators(validator).unconsumedPremiums.values.sum
    val stateOfValidator: ValidatorState = validators(validator).resetUnconsumedPremiums
    val book: ValidatorsBook = new ValidatorsBook(validators + (validator -> stateOfValidator), bondingBuffer, unbondingBuffer, cachedNumberOfActiveValidators, cachedTotalStake)
    return (book, totalRewards)
  }

}

object ValidatorsBook {

  def empty = new ValidatorsBook(
    validators = Map.empty[ValidatorId, ValidatorState],
    bondingBuffer = Seq.empty[BondingQueueItem],
    unbondingBuffer = Seq.empty[UnbondingQueueItem],
    cachedNumberOfActiveValidators = 0,
    cachedTotalStake = 0)

  sealed abstract class BondingQueueAppendResult

  object BondingQueueAppendResult {
    case object OK extends BondingQueueAppendResult
    case object StakeChangeTooSmall extends BondingQueueAppendResult
    case object StakeChangeTooLarge extends BondingQueueAppendResult
    case object EffectiveStakeBelowMin extends BondingQueueAppendResult
    case object EffectiveStakeAboveMax extends BondingQueueAppendResult
    case object NumberOfItemsInSlidingWindowExceeded extends BondingQueueAppendResult
    case object SumOfItemsInSlidingWindowExceeded extends BondingQueueAppendResult
  }
}
