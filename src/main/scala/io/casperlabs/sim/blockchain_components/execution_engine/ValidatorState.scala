package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.{BlockId, ValidatorId}
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorState.UnconsumedBlockRewardInfo
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

import scala.collection.immutable.Queue

/**
  * Read only data structure, which is part of the implementation of ValidatorsBook.
  * Keeps info about one validator.
  *
  * @param id id of the validator
  * @param account owning account; bonding and unbonding moves ether between this account and internal "stake" pseudo-account
  * @param stake pseudo-account where the stake of the validator is kept
  * @param bondingEscrow pseudo-account where the bonding escrow is kept (ether took for future bonding while appending a new bonding request to the waiting queue)
  * @param unbondingEscrow pseudo-account where the unbonding escrow is kept (ether took from stake while appending a new unbonding request to the waiting queue)
  * @param unconsumedBlockRewards collection of block rewards this validator can claim
  */
private[execution_engine] class ValidatorState private (
                               val id: ValidatorId,
                               val account: Account,
                               val stake: Ether,
                               val bondingEscrow: Ether,
                               val unbondingEscrow: Ether,
                               val unconsumedBlockRewards: Queue[UnconsumedBlockRewardInfo]
                            )
{

  def registerBlockRewardDue(blockId: BlockId, amount: Ether, pTime: Gas): ValidatorState =
    if (amount == 0) //this actually happens as a corner case, where the block burned only tiny amount of gas and the stake of validator is so small,
      this           //that effective reward is less than 0 ether; then rounding comes into play and we end up with zero
    else
      new ValidatorState(id, account, stake, bondingEscrow, unbondingEscrow, unconsumedBlockRewards.enqueue(UnconsumedBlockRewardInfo(pTime, blockId, amount)))

  def bondingEscrowLock(delta: Ether): ValidatorState = new ValidatorState(id, account, stake, bondingEscrow + delta, unbondingEscrow, unconsumedBlockRewards)

  def unbondingEscrowRelease(delta: Ether): ValidatorState = new ValidatorState(id, account, stake, bondingEscrow, unbondingEscrow - delta, unconsumedBlockRewards)

  def increaseStake(delta: Ether): ValidatorState = {
    assert (bondingEscrow - delta >= 0)
    assert (delta > 0)
    return new ValidatorState(id, account, stake + delta, bondingEscrow - delta, unbondingEscrow, unconsumedBlockRewards)
  }

  def decreaseStake(delta: Ether): ValidatorState = {
    assert (stake - delta >= 0)
    assert (delta > 0)
    return new ValidatorState(id, account, stake - delta, bondingEscrow, unbondingEscrow + delta, unconsumedBlockRewards)
  }


  def resetUnconsumedBlockRewards: ValidatorState = new ValidatorState(id, account, stake, bondingEscrow, unbondingEscrow, Queue.empty[UnconsumedBlockRewardInfo])

  def isReadyToBeForgotten: Boolean = stake == 0 && bondingEscrow == 0 && unbondingEscrow == 0

  def updateDigest(digester: CryptographicDigester): Unit = {
    digester.updateWith(id)
    digester.updateWith(account)
    digester.updateWith(stake)
    digester.updateWith(bondingEscrow)
    digester.updateWith(unbondingEscrow)
    for (item <- unconsumedBlockRewards) {
      digester.updateWith(item.amount)
      digester.updateWithHash(item.blockId)
      digester.updateWith(item.pTimeWhenEarned)
    }
  }

}

object ValidatorState {

  def initial(id: ValidatorId, account: Account, stake: Ether) =
    new ValidatorState(id, account, stake, 0, 0, Queue.empty[UnconsumedBlockRewardInfo])


  case class UnconsumedBlockRewardInfo(pTimeWhenEarned: Gas, blockId: BlockId, amount: Ether) extends Ordered[UnconsumedBlockRewardInfo] {
    override def compare(that: UnconsumedBlockRewardInfo): Int = this.pTimeWhenEarned.compareTo(that.pTimeWhenEarned)
  }

}