package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace

import scala.collection.immutable.HashMap

/**
  * Representation of the state of blockchain computer.
  *
  * @param memoryState state of programs memory (memory on which smart contracts operate)
  * @param userAccounts state of user accounts
  * @param validators weights of validators
  * @param transactionsCostBuffer //todo explain this field
  * @tparam MS
  */
case class GlobalState[MS](
                        memoryState: MS,
                        userAccounts: Map[Account, UserAccountState],
                        validators: Map[NodeId, ValidatorState],
                        transactionsCostBuffer: Ether
                      ) {

  def userBalance(account: Account): Ether = userAccounts(account).balance

  def numberOfActiveValidators: Int = validators count { case (node, vs) => vs.isActive }

}

object GlobalState {

  def empty[P,MS](computingSpace: ComputingSpace[P,MS]): GlobalState[MS] = GlobalState[MS](
    memoryState = computingSpace.initialState,
    userAccounts = new HashMap[Account, UserAccountState],
    validators = new HashMap[NodeId, ValidatorState],
    transactionsCostBuffer = 0
  )

}

case class ValidatorState(account: Account, stake: Ether, unconsumedPremiums: Ether, isActive: Boolean)

case class UserAccountState(nonce: Long, balance: Ether)
