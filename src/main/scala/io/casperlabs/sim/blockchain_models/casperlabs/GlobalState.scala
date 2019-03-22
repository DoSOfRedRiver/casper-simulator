package io.casperlabs.sim.blockchain_models.casperlabs

import io.casperlabs.sim.blockchain_models.casperlabs.Transaction._

import scala.collection.immutable.HashMap

case class GlobalState(sharedDatabase: Map[Slot, Int], userAccounts: Map[Account, UserAccountState], validators: Map[Node, ValidatorState], transactionsCostBuffer: Ether, config: BlockchainConfig) {

  def userBalance(account: Account): Ether = userAccounts(account).balance

  def numberOfActiveValidators: Int = validators count { case (node, vs) => vs.isActive}

  def applyTransaction(transaction: Transaction): (GlobalState, TransactionResult) = {
    assert(userAccounts.contains(transaction.sponsor))

    if (userAccounts(transaction.sponsor).nonce != transaction.nonce)
      return (this, TransactionResult.NonceMismatch(userAccounts(transaction.sponsor).nonce, transaction.nonce))

    if (this.userBalance(transaction.sponsor) < transaction.gasLimit * transaction.gasPrice)
      return (this, TransactionResult.GasLimitNotCoveredBySponsorAccountBalance(transaction.gasLimit, transaction.gasLimit * transaction.gasPrice, this.userBalance(transaction.sponsor)))

    if (transaction.gasUsed > transaction.gasLimit)
      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.GasLimitExceeded(transaction.gasLimit, transaction.gasUsed))

    transaction match {

      case tx: AccountCreation =>
        assert(! userAccounts.contains(tx.newAccount))
        val gs = GlobalState(
          sharedDatabase,
          userAccounts + (tx.newAccount -> UserAccountState(nonce = 0L, balance = 0)),
          validators,
          transactionsCostBuffer,
          config)
        return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)

      case tx: DatabaseUpdate =>
        val mutatingActions = tx.operations filterNot { case (slot,action) => action == DbAtomicAction.Read }
        val actualMutationsAsMap: Map[Slot, Int] = mutatingActions map { case (slot,action) =>
          action match {
            case DbAtomicAction.Inc => (slot, sharedDatabase(slot) + 1)
            case DbAtomicAction.Write(n) => (slot, sharedDatabase(slot) + n)
            case DbAtomicAction.Read => throw new RuntimeException("impossible")
          }
        }
        val gs = GlobalState(
          sharedDatabase ++ actualMutationsAsMap,
          userAccounts,
          validators,
          transactionsCostBuffer,
          config)
        return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)

      case tx: EtherTransfer =>
        val sponsorBalance = userBalance(tx.sponsor)
        if (tx.value + transaction.costIfSuccessful > sponsorBalance)
          return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.AccountBalanceInsufficient(sponsorBalance))
        else {
          val oldStateSponsor = userAccounts(tx.sponsor)
          val newStateSponsor = UserAccountState(oldStateSponsor.nonce, oldStateSponsor.balance - tx.value)
          val oldStateTarget = userAccounts(tx.targetAccount)
          val newStateTarget = UserAccountState(oldStateTarget.nonce, oldStateTarget.balance + tx.value)
          val gs = GlobalState(
            sharedDatabase,
            userAccounts + (tx.sponsor -> newStateSponsor) + (tx.targetAccount -> newStateTarget),
            validators,
            transactionsCostBuffer,
            config
          )
          return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)
        }

      case tx: ValidatorInit =>
        assert (! validators.contains(tx.node))
        val sponsorBalance = userBalance(tx.sponsor)
        if (transaction.costIfSuccessful > sponsorBalance)
          return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.AccountBalanceInsufficient(sponsorBalance))
        else {
          val gs = GlobalState(
            sharedDatabase,
            userAccounts,
            validators + (tx.node -> ValidatorState(tx.sponsor, 0, 0, isActive = false)),
            transactionsCostBuffer,
            config
          )
          return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)
        }

      case tx: Bonding =>
        assert (validators.contains(tx.node))
        assert (tx.sponsor == validators(tx.node).account)
        val sponsorBalance = userBalance(tx.sponsor)
        if (tx.value + transaction.costIfSuccessful > sponsorBalance)
          return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.AccountBalanceInsufficient(sponsorBalance))
        else {
          val oldStateAccount = userAccounts(tx.sponsor)
          val newStateAccount = UserAccountState(oldStateAccount.nonce, oldStateAccount.balance - tx.value)
          val oldStateValidator = validators(tx.node)
          val newStake = oldStateValidator.stake + tx.value
          val newStateValidator = ValidatorState(oldStateValidator.account, newStake, oldStateValidator.unconsumedPremiums, isActive = newStake >= config.minimalValidatorStake)
          val gs = GlobalState(
            sharedDatabase,
            userAccounts + (tx.sponsor -> newStateAccount),
            validators + (tx.node -> newStateValidator),
            transactionsCostBuffer,
            config
          )
          return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)
        }

      case tx: Unbonding =>
        assert (validators.contains(tx.node))
        assert (tx.sponsor == validators(tx.node).account)
        val oldStateValidator = validators(tx.node)
        if (oldStateValidator.stake < tx.value)
          return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.UnbondingOverdrive(oldStateValidator.stake, tx.value))
        else {
          val oldStateAccount = userAccounts(tx.sponsor)
          val newStateAccount = UserAccountState(oldStateAccount.nonce, oldStateAccount.balance + tx.value)
          val newStake = oldStateValidator.stake - tx.value
          val newStateValidator = ValidatorState(oldStateValidator.account, newStake, oldStateValidator.unconsumedPremiums, isActive = newStake >= config.minimalValidatorStake)
          val gs = GlobalState(
            sharedDatabase,
            userAccounts + (tx.sponsor -> newStateAccount),
            validators + (tx.node -> newStateValidator),
            transactionsCostBuffer,
            config
          )
          return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionResult.Success)
        }

      case x: EquivocationSlashing => return (this, TransactionResult.Success) //todo

    }

  }

  def applyBlock(block: NormalBlock): GlobalState = {
    //apply all transactions
    val globalStateAfterAllTransactions = block.transactions.foldLeft(this) { case (gs, t) => gs.applyTransaction(t)._1 }

    //distribute total gas payment across validator accounts
    val etherToBeDistributed = globalStateAfterAllTransactions.transactionsCostBuffer
    val numberOfValidators = this.numberOfActiveValidators
    val profitPerValidator: Ether = etherToBeDistributed / numberOfValidators
    val profitRemainder: Ether = etherToBeDistributed % numberOfValidators

    val updatedValidators: Map[Node, ValidatorState] = globalStateAfterAllTransactions.validators.map {
      case (n, vs) =>
        if (vs.isActive) {
          if (n == block.creator)
            n -> ValidatorState(vs.account, vs.stake, 0, vs.isActive)
          else
            n -> ValidatorState(vs.account, vs.stake, vs.unconsumedPremiums + profitPerValidator, vs.isActive)
        }
        else
          (n, vs)
    }

    //transfer the accumulated profit to miner's account
    val minerAccountId: Account = validators(block.creator).account
    val oldStateOfMinerAccount: UserAccountState = globalStateAfterAllTransactions.userAccounts(minerAccountId)
    val newStateOfMinerAccount: UserAccountState =
      UserAccountState(oldStateOfMinerAccount.nonce, oldStateOfMinerAccount.balance + validators(block.creator).unconsumedPremiums + profitPerValidator + profitRemainder)
    val updatedUserAccounts = globalStateAfterAllTransactions.userAccounts + (minerAccountId -> newStateOfMinerAccount)

    //put together the new snapshot of global state
    return GlobalState(
      globalStateAfterAllTransactions.sharedDatabase,
      updatedUserAccounts,
      updatedValidators,
      0,
      config
    )
  }

  def withCostOfTransactionPaidAndNonceIncremented(transaction: Transaction): GlobalState = {
    val oldState = userAccounts(transaction.sponsor)
    val newState = UserAccountState(oldState.nonce + 1, oldState.balance - transaction.costCappedByGasLimit)
    return GlobalState(
      sharedDatabase,
      userAccounts + (transaction.sponsor -> newState),
      validators,
      transactionsCostBuffer + transaction.costIfSuccessful,
      config
    )
  }

}

object GlobalState {
  def empty(config: BlockchainConfig): GlobalState = GlobalState(
    sharedDatabase = new HashMap[Slot, Int],
    userAccounts = new HashMap[Account, UserAccountState],
    validators = new HashMap[Node, ValidatorState],
    transactionsCostBuffer = 0,
    config
  )
}

case class ValidatorState(account: Account, stake: Ether, unconsumedPremiums: Ether, isActive: Boolean)

case class UserAccountState(nonce: Long, balance: Ether)
