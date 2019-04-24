package io.casperlabs.sim.blockchain_components.execution_engine

import scala.collection.immutable.SortedMap

class ValidatorState private (
                               val id: ValidatorId,
                               val account: Account,
                               val stake: Ether,
                               val bondingEscrow: Ether,
                               val unbondingEscrow: Ether,
                               val unconsumedPremiums: SortedMap[Gas, Ether]
                         )
{

  def storeBlockReward(amount: Ether, pTime: Gas): ValidatorState = {
    val currentPremiumForThisTimepoint: Ether = unconsumedPremiums.getOrElse(pTime, 0)
    val updatedMap: SortedMap[Gas, Ether] = unconsumedPremiums + (pTime -> (amount + currentPremiumForThisTimepoint))
    new ValidatorState(id, account, stake, bondingEscrow, unbondingEscrow, updatedMap)
  }

  def resetUnconsumedPremiums: ValidatorState = new ValidatorState(id, account, stake, bondingEscrow, unbondingEscrow, SortedMap.empty[Gas, Ether])

}

object ValidatorState {

  def initial(id: ValidatorId, account: Account, stake: Ether) =
    new ValidatorState(id, account, stake, 0, 0, SortedMap.empty[Gas, Ether])


}