package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace

/**
  * Representation of the state of blockchain computer.
  *
  * @param memoryState state of the memory (this is however only the memory used by aur representation of smart contracts)
  * @param accounts state of the accounts collection
  * @param validatorsBook state of the validators collection
  * @param pTime block-time (cumulative gas burned by transactions executed along the evolution that led to this global state)
  * @tparam MS type of memory states (this comes from a 'computing space' in use - see ComputingSpace class)
  */
case class GlobalState[MS](memoryState: MS, accounts: AccountsRegistry, validatorsBook: ValidatorsBook, pTime: Gas) {

  /**
    * Current balance (in tokens, i.e. ether) of the selected account.
    *
    * @param account account id
    * @return number of tokens
    */
  def accountBalance(account: Account): Ether = accounts.getBalanceOf(account)

  /**
    * Number of validators in the current stake map. Only validators that have non-zero stake are considered 'active'.
    */
  def numberOfActiveValidators: Int = validatorsBook.numberOfValidators

  /**
    * Creates a copy of this global state with an atomic transfer of tokens executed.
    * It means that the balance of source account gets decreased and the balance of destination account gets increased.
    * Destination account will be created if needed.
    *
    * @param from   source account
    * @param to     destination account
    * @param amount number of tokens (= ether)
    * @return new global state, with the transfer executed
    * @throws RuntimeException if source account does not exist of has insufficient balance
    */
  def transfer(from: Account, to: Account, amount: Ether): GlobalState[MS] = GlobalState(memoryState, accounts.transfer(from, to, amount), validatorsBook, pTime)

  def updateAccountBalance(account: Account, delta: Ether): GlobalState[MS] = GlobalState(memoryState, accounts.updateBalance(account, delta), validatorsBook, pTime)

}

object GlobalState {

  def empty[P,MS](computingSpace: ComputingSpace[P,MS]): GlobalState[MS] = GlobalState[MS](
    memoryState = computingSpace.initialState,
    accounts = AccountsRegistry.empty,
    validatorsBook = ValidatorsBook.empty,
    pTime = 0L
  )

}

