package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_models.casperlabs_classic.NormalBlock

/**
  * Default (minimalistic) implementation of an execution engine for proof-of-stake blockchains.
  *
  * Contains emulation of:
  * 1. Smart contracts frameworks (pluggable - thanks to ComputingSpace abstraction).
  * 2. Internal money (= ether).
  * 3. PoS support (explicit bonding/unbonding/slashing operations)
  * 4. Execution cost (= gas).
  * 5. User accounts.
  *
  * @param config
  * @tparam CS
  * @tparam P
  * @tparam MS
  */
class ExecutionEngine[CS <: ComputingSpace[P, MS], P, MS](config: BlockchainConfig, computingSpace: CS) {

  /**
    * Executes transaction against given global state, producing a new global state.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transaction transaction to be executed
    * @return a pair (resulting global state, execution status)
    */
  def executeTransaction(gs: GlobalState[MS], transaction: Transaction): (GlobalState[MS], TransactionExecutionResult) = {
    assert(gs.userAccounts.contains(transaction.sponsor))

    if (gs.userAccounts(transaction.sponsor).nonce != transaction.nonce)
      return (gs, TransactionExecutionResult.NonceMismatch(gasBurned = 0, gs.userAccounts(transaction.sponsor).nonce, transaction.nonce))

    if (gs.userBalance(transaction.sponsor) < transaction.gasLimit * transaction.gasPrice)
      return (gs, TransactionExecutionResult.GasLimitNotCoveredBySponsorAccountBalance(gasBurned = 0, transaction.gasLimit, transaction.gasLimit * transaction.gasPrice, gs.userBalance(transaction.sponsor)))

//    if (transaction.gasUsed > transaction.gasLimit)
//      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionStatus.GasLimitExceeded(transaction.gasLimit, transaction.gasUsed))

    val (globalStateAfterTransactionExecution, txResult): (GlobalState[MS], TransactionExecutionResult) = transaction match {
      case tx: Transaction.AccountCreation => this.executeAccountCreation(gs, tx)
      case tx: Transaction.SmartContractExecution[CS,P,MS] => this.executeSmartContract(gs, tx)
      case tx: Transaction.EtherTransfer => this.executeEtherTransfer(gs, tx)
      case tx: Transaction.ValidatorInit => this.executeValidatorInit(gs, tx)
      case tx: Transaction.Bonding => this.executeBonding(gs, tx)
      case tx: Transaction.Unbonding => this.executeUnbonding(gs, tx)
      case tx: Transaction.EquivocationSlashing => this.executeEquivocationSlashing(gs,tx)
    }

    val oldUserAccountState: UserAccountState = gs.userAccounts(transaction.sponsor)
    val gasCappedByGasLimit: Gas = math.min(txResult.gasBurned, transaction.gasLimit) * transaction.gasPrice
    val newUserAccountState: UserAccountState = UserAccountState(oldUserAccountState.nonce + 1, oldUserAccountState.balance - gasCappedByGasLimit)

    val effectiveGS = GlobalState[MS](
      globalStateAfterTransactionExecution.memoryState,
      globalStateAfterTransactionExecution.userAccounts + (transaction.sponsor -> newUserAccountState),
      globalStateAfterTransactionExecution.validators,
      globalStateAfterTransactionExecution.transactionsCostBuffer + gasCappedByGasLimit
    )

    return (effectiveGS, txResult)
  }

//################################################ PER-TRANSACTION-TYPE SECTIONS #####################################################################

  private def executeAccountCreation(gs: GlobalState[MS], tx: Transaction.AccountCreation): (GlobalState[MS], TransactionExecutionResult) = {
    assert(! gs.userAccounts.contains(tx.newAccount))
    val result = GlobalState(
      gs.memoryState,
      gs.userAccounts + (tx.newAccount -> UserAccountState(nonce = 0L, balance = 0)),
      gs.validators,
      gs.transactionsCostBuffer)
    return (gs, TransactionExecutionResult.Success(config.accountCreationCost))
  }

  private def executeSmartContract(gs: GlobalState[MS], tx: Transaction.SmartContractExecution[CS,P,MS]): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???

    //    val mutatingActions = tx.operations filterNot { case (slot,action) => action == DbAtomicAction.Read }
//    val actualMutationsAsMap: Map[Slot, Int] = mutatingActions map { case (slot,action) =>
//      action match {
//        case DbAtomicAction.Inc => (slot, sharedDatabase(slot) + 1)
//        case DbAtomicAction.Write(n) => (slot, sharedDatabase(slot) + n)
//        case DbAtomicAction.Read => throw new RuntimeException("impossible")
//      }
//    }
//    val gs = GlobalState(
//      sharedDatabase ++ actualMutationsAsMap,
//      userAccounts,
//      validators,
//      transactionsCostBuffer,
//      config)
//    return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.Success)
  }

  private def executeEtherTransfer(gs: GlobalState[MS], tx: Transaction.EtherTransfer): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???

//    val sponsorBalance = userBalance(tx.sponsor)
//    if (tx.value + transaction.costIfSuccessful > sponsorBalance)
//      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.AccountBalanceInsufficient(sponsorBalance))
//    else {
//      val oldStateSponsor = userAccounts(tx.sponsor)
//      val newStateSponsor = UserAccountState(oldStateSponsor.nonce, oldStateSponsor.balance - tx.value)
//      val oldStateTarget = userAccounts(tx.targetAccount)
//      val newStateTarget = UserAccountState(oldStateTarget.nonce, oldStateTarget.balance + tx.value)
//      val gs = GlobalState(
//        sharedDatabase,
//        userAccounts + (tx.sponsor -> newStateSponsor) + (tx.targetAccount -> newStateTarget),
//        validators,
//        transactionsCostBuffer,
//        config
//      )
//      return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.Success)
//    }
  }

  private def executeValidatorInit(gs: GlobalState[MS], tx: Transaction.ValidatorInit): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???

//    assert (! validators.contains(tx.node))
//    val sponsorBalance = userBalance(tx.sponsor)
//    if (transaction.costIfSuccessful > sponsorBalance)
//      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.AccountBalanceInsufficient(sponsorBalance))
//    else {
//      val gs = GlobalState(
//        sharedDatabase,
//        userAccounts,
//        validators + (tx.node -> ValidatorState(tx.sponsor, 0, 0, isActive = false)),
//        transactionsCostBuffer,
//        config
//      )
//      return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.Success)
//    }
  }

  private def executeBonding(gs: GlobalState[MS], tx: Transaction.Bonding): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???

//    assert (validators.contains(tx.node))
//    assert (tx.sponsor == validators(tx.node).account)
//    val sponsorBalance = userBalance(tx.sponsor)
//    if (tx.value + transaction.costIfSuccessful > sponsorBalance)
//      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.AccountBalanceInsufficient(sponsorBalance))
//    else {
//      val oldStateAccount = userAccounts(tx.sponsor)
//      val newStateAccount = UserAccountState(oldStateAccount.nonce, oldStateAccount.balance - tx.value)
//      val oldStateValidator = validators(tx.node)
//      val newStake = oldStateValidator.stake + tx.value
//      val newStateValidator = ValidatorState(oldStateValidator.account, newStake, oldStateValidator.unconsumedPremiums, isActive = newStake >= config.minimalValidatorStake)
//      val gs = GlobalState(
//        sharedDatabase,
//        userAccounts + (tx.sponsor -> newStateAccount),
//        validators + (tx.node -> newStateValidator),
//        transactionsCostBuffer,
//        config
//      )
//      return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.Success)
//    }
  }

  private def executeUnbonding(gs: GlobalState[MS], tx: Transaction.Unbonding): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???

//    assert (validators.contains(tx.node))
//    assert (tx.sponsor == validators(tx.node).account)
//    val oldStateValidator = validators(tx.node)
//    if (oldStateValidator.stake < tx.value)
//      return (this.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.UnbondingOverdrive(oldStateValidator.stake, tx.value))
//    else {
//      val oldStateAccount = userAccounts(tx.sponsor)
//      val newStateAccount = UserAccountState(oldStateAccount.nonce, oldStateAccount.balance + tx.value)
//      val newStake = oldStateValidator.stake - tx.value
//      val newStateValidator = ValidatorState(oldStateValidator.account, newStake, oldStateValidator.unconsumedPremiums, isActive = newStake >= config.minimalValidatorStake)
//      val gs = GlobalState(
//        sharedDatabase,
//        userAccounts + (tx.sponsor -> newStateAccount),
//        validators + (tx.node -> newStateValidator),
//        transactionsCostBuffer,
//        config
//      )
//      return (gs.withCostOfTransactionPaidAndNonceIncremented(transaction), TransactionExecutionResult.Success)
//    }

  }

  private def executeEquivocationSlashing(gs: GlobalState[MS], tx: Transaction.EquivocationSlashing): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: fix this
    ???
  }

//#############################################################################################################################################

  def applyBlock(block: NormalBlock): GlobalState[MS] = {
    //todo: fix this
    ???

//    //apply all transactions
//    val globalStateAfterAllTransactions = block.transactions.foldLeft(this) { case (gs, t) => gs.applyTransaction(t)._1 }
//
//    //distribute total gas payment across validator accounts
//    val etherToBeDistributed = globalStateAfterAllTransactions.transactionsCostBuffer
//    val numberOfValidators = this.numberOfActiveValidators
//    val profitPerValidator: Ether = etherToBeDistributed / numberOfValidators
//    val profitRemainder: Ether = etherToBeDistributed % numberOfValidators
//
//    val updatedValidators: Map[Node, ValidatorState] = globalStateAfterAllTransactions.validators.map {
//      case (n, vs) =>
//        if (vs.isActive) {
//          if (n == block.creator)
//            n -> ValidatorState(vs.account, vs.stake, 0, vs.isActive)
//          else
//            n -> ValidatorState(vs.account, vs.stake, vs.unconsumedPremiums + profitPerValidator, vs.isActive)
//        }
//        else
//          (n, vs)
//    }
//
//    //transfer the accumulated profit to miner's account
//    val minerAccountId: Account = validators(block.creator).account
//    val oldStateOfMinerAccount: UserAccountState = globalStateAfterAllTransactions.userAccounts(minerAccountId)
//    val newStateOfMinerAccount: UserAccountState =
//      UserAccountState(oldStateOfMinerAccount.nonce, oldStateOfMinerAccount.balance + validators(block.creator).unconsumedPremiums + profitPerValidator + profitRemainder)
//    val updatedUserAccounts = globalStateAfterAllTransactions.userAccounts + (minerAccountId -> newStateOfMinerAccount)
//
//    //put together the new snapshot of global state
//    return GlobalState(
//      globalStateAfterAllTransactions.sharedDatabase,
//      updatedUserAccounts,
//      updatedValidators,
//      0,
//      config
//    )
  }


}
