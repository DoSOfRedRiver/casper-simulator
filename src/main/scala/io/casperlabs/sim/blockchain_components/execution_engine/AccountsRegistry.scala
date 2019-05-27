package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.hashing.CryptographicDigester

/**
  * Immutable data structure we use to keep complete information about accounts (as seen on-chain).
  * This is part of global state.
  */
class AccountsRegistry (m: Map[Account, AccountState]) {

  def addAccount(account: Account): AccountsRegistry = {
    assert (! m.contains(account))
    return new AccountsRegistry(m + (account -> AccountState.empty))
  }

  /**
    * Returns current balance (in ether) of the specified account.
    *
    * @param account account id
    * @return balance in ether
    */
  def getBalanceOf(account: Account): Ether =
    m.get(account) match {
      case Some(state) => state.balance
      case None => 0
    }

  def getNonceOf(account: Account): Long =
    m.get(account) match {
      case Some(state) => state.nonce
      case None => 0L
    }

  def apply(index: Account): AccountState = m(index)

  /**
    * Create copy of accounts registry with the specified ether transfer executed.
    * Caution: this is a low-level operation, only balances are updated (nonces are not !).
    * It will blow if source account does not have enough ether.
    *
    * @param from source account id
    * @param to target account id
    * @param amount amount of tokens (ether) to be transferred
    * @return
    */
  def transfer(from: Account, to: Account, amount: Ether): AccountsRegistry = {
    assert(amount > 0)

    m.get(from) match {
      case Some(sourceAccountState) =>
        assert(sourceAccountState.balance >= amount)
        val targetAccountState = m.getOrElse(to, AccountState.empty)
        val sourceAccountStateAfterUpdate = AccountState(sourceAccountState.nonce, sourceAccountState.balance - amount)
        val targetAccountStateAfterUpdate = AccountState(targetAccountState.nonce, targetAccountState.balance + amount)
        return new AccountsRegistry(m + (from -> sourceAccountStateAfterUpdate) + (to -> targetAccountStateAfterUpdate))

      case None =>
        throw new RuntimeException(s"low-level transfer from account $from failed because source account was not existing")
    }
  }

  def updateBalance(account: Account, delta: Ether): AccountsRegistry = {
    val oldState = m.getOrElse(account, AccountState.empty)
    assert(oldState.balance + delta >= 0)
    val newState = AccountState(oldState.nonce, oldState.balance + delta)
    return new AccountsRegistry(m + (account -> newState))
  }

  def updateBalances(updates: Map[Account, Ether]): AccountsRegistry = {
    val mapOfUpdatedAccountStates = updates map { case (acc, delta) =>
      val oldAccountState = m(acc)
      val newAccountState = AccountState(oldAccountState.nonce, oldAccountState.balance + delta)
      assert(newAccountState.balance > 0)
      (acc, newAccountState)
    }

    return new AccountsRegistry(m ++ mapOfUpdatedAccountStates)
  }

  def updateBalanceAndIncreaseNonce(account: Account, delta: Ether): AccountsRegistry = {
    val oldState = m.getOrElse(account, AccountState.empty)
    assert(oldState.balance + delta >= 0)
    val newState = AccountState(oldState.nonce + 1, oldState.balance + delta)
    return new AccountsRegistry(m + (account -> newState))
  }

  def contains(account: Account): Boolean = m.contains(account)

  def updateDigest(digester: CryptographicDigester): Unit = {
    for ((account, state) <- m) {
      digester.updateWith(account)
      digester.updateWith(state.nonce)
      digester.updateWith(state.balance)
    }
  }

}

object AccountsRegistry {

  val empty: AccountsRegistry = new AccountsRegistry(Map.empty[Account, AccountState])

  case class AccountState(nonce: Long, balance: Ether)

  object AccountState {
    val empty: AccountState = AccountState(0,0)
  }

}
