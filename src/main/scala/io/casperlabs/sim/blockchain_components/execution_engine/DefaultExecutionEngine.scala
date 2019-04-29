package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, ExecutionEngine}
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.hashing.Sha256Hash

/**
  * Default (minimalistic) implementation of an execution engine for proof-of-stake blockchains.
  *
  * The following features are implemented here:
  * 1. Smart contracts framework emulation (pluggable - thanks to ComputingSpace abstraction).
  * 2. Internal money (= ether).
  * 3. PoS support (explicit bonding/unbonding/slashing operations).
  * 4. Execution cost (= gas).
  * 5. User accounts.
  *
  * @param config
  * @tparam CS
  * @tparam P
  * @tparam MS
  */
class DefaultExecutionEngine[CS <: ComputingSpace[P, MS], P, MS](config: BlockchainConfig, computingSpace: CS) extends ExecutionEngine[MS] {

  /**
    * Executes transaction against given global state, producing a new global state.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transaction transaction to be executed
    * @param effectiveGasPrice gas price to be used by this transaction (usually will be different that gas price offered be the creator of this transaction)
    * @param blockTime "current block time" to be used during the execution
    * @return a pair (resulting global state, execution status)
    */
  def executeTransaction(gs: GlobalState[MS], transaction: Transaction, effectiveGasPrice: Ether, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult) = {
    //should be checked earlier; if the check fails here then we apparently have a bug
    assert(gs.accounts.contains(transaction.sponsor))

    //nonce mismatch forces fail fast; no gas will be consumed from sponsor account
    if (gs.accounts.getNonceOf(transaction.sponsor) != transaction.nonce)
      return (gs, TransactionExecutionResult.NonceMismatch(gasBurned = 0, gs.accounts.getNonceOf(transaction.sponsor), transaction.nonce))

    //before executing the transaction we require that sponsor has enough ether to cover the maximum cost of transaction he declared to cover
    if (gs.accountBalance(transaction.sponsor) < transaction.gasLimit * effectiveGasPrice)
      return (gs, TransactionExecutionResult.GasLimitNotCoveredBySponsorAccountBalance(gasBurned = 0, transaction.gasLimit, transaction.gasLimit * transaction.gasPrice, gs.accountBalance(transaction.sponsor)))

    //here we execute the per-transaction-type specific part; caution: returned global state does not have transaction cost paid yet !
    val (gsAfterTransactionExecution, txResult): (GlobalState[MS], TransactionExecutionResult) = transaction match {
      case tx: Transaction.AccountCreation => this.executeAccountCreation(gs, tx)
      case tx: Transaction.SmartContractExecution[CS,P,MS] => this.executeSmartContract(gs, tx)
      case tx: Transaction.EtherTransfer => this.executeEtherTransfer(gs, tx)
      case tx: Transaction.Bonding => this.executeBonding(gs, tx, blockTime)
      case tx: Transaction.Unbonding => this.executeUnbonding(gs, tx, blockTime)
      case tx: Transaction.EquivocationSlashing => this.executeEquivocationSlashing(gs,tx)
    }

    //now we know what the actual transaction cost (= gas burned) was
    //so we are able to calculate new user account state (nonce and ether must be updated)
    //but we still have two violations possible:
    //1. actual gas burned could exceed gas limit set by the sponsor
    //2. amount of ether left on the account may not be enough to cover transaction cost (because the transaction itself could take some money - for example bonding does this !)

    val gasCappedByGasLimit: Gas = math.min(txResult.gasBurned, transaction.gasLimit)

    //case 1: gas limit exceeded
    if (txResult.gasBurned > transaction.gasLimit) {
      val updatedGS = GlobalState[MS](
        memoryState = gs.memoryState,
        accounts = gsAfterTransactionExecution.accounts.updateBalanceAndIncreaseNonce(transaction.sponsor, - gasCappedByGasLimit * effectiveGasPrice),
        validatorsBook = gsAfterTransactionExecution.validatorsBook
      )
      return (updatedGS, TransactionExecutionResult.GasLimitExceeded(
        gasBurned = gasCappedByGasLimit,
        gasReallyNeededForSuccessfulExecution = txResult.gasBurned,
        gasLimit = transaction.gasLimit)
      )
    }

    //case 2: not enough ether left to cover transaction cost
    val sponsorBalanceAfterTxExecution: Ether = gsAfterTransactionExecution.accountBalance(transaction.sponsor)
    if (sponsorBalanceAfterTxExecution < txResult.gasBurned * effectiveGasPrice) {
      val updatedGS = GlobalState[MS](
        memoryState = gs.memoryState,
        accounts = gsAfterTransactionExecution.accounts.updateBalanceAndIncreaseNonce(transaction.sponsor, - gasCappedByGasLimit * effectiveGasPrice),
        validatorsBook = gsAfterTransactionExecution.validatorsBook
      )
      return (updatedGS, TransactionExecutionResult.AccountBalanceLeftInsufficientForCoveringGasCostAfterTransactionWasExecuted(
        gasBurned = gasCappedByGasLimit,
        gasBurnedIfSuccessful = txResult.gasBurned,
        gasCostIfSuccessful = txResult.gasBurned * effectiveGasPrice,
        balanceOfSponsorAccountWhenGasCostFailedAttemptHappened = sponsorBalanceAfterTxExecution)
      )
    }

    //case 3: everything looks good, this is a happy path
    val updatedGS = GlobalState[MS](
        memoryState = gsAfterTransactionExecution.memoryState,
        accounts = gsAfterTransactionExecution.accounts.updateBalanceAndIncreaseNonce(transaction.sponsor, - txResult.gasBurned * effectiveGasPrice),
        validatorsBook = gsAfterTransactionExecution.validatorsBook
      )
    return (updatedGS, txResult)
  }

  /**
    * Calculate hash of given global state.
    *
    * @param gs global state
    * @return Sha-256 hash
    */
  def globalStateHash(gs: GlobalState[MS]): Sha256Hash = {
    //todo
    ???
  }

//################################################ PER-TRANSACTION-TYPE SPECIFIC PARTS #####################################################################

  private def executeAccountCreation(gs: GlobalState[MS], tx: Transaction.AccountCreation): (GlobalState[MS], TransactionExecutionResult) = {
    assert(! gs.accounts.contains(tx.newAccount))
    val updatedGS = GlobalState(
      memoryState = gs.memoryState,
      accounts = gs.accounts.addAccount(tx.newAccount),
      validatorsBook = gs.validatorsBook)
    return (updatedGS, TransactionExecutionResult.Success(config.accountCreationCost))
  }

  private def executeSmartContract(gs: GlobalState[MS], tx: Transaction.SmartContractExecution[CS,P,MS]): (GlobalState[MS], TransactionExecutionResult) = {
    val programResult: computingSpace.ProgramResult = computingSpace.execute(tx.program, gs.memoryState)

    return programResult.ms match {
      case Some(newMemoryState) => (GlobalState[MS](newMemoryState, gs.accounts, gs.validatorsBook), TransactionExecutionResult.Success(programResult.gasUsed))
      case None => (gs, TransactionExecutionResult.SmartContractUnhandledException(programResult.gasUsed))
    }
  }

  private def executeEtherTransfer(gs: GlobalState[MS], tx: Transaction.EtherTransfer): (GlobalState[MS], TransactionExecutionResult) = {
    return if (gs.accountBalance(tx.sponsor) >= tx.value)
      (gs.transfer(tx.sponsor, tx.targetAccount, tx.value), TransactionExecutionResult.Success(config.transferCost))
    else
      (gs, TransactionExecutionResult.AccountBalanceInsufficientForTransfer(config.transferCost, tx.value, gs.accountBalance(tx.sponsor)))
  }

  private def executeBonding(gs: GlobalState[MS], tx: Transaction.Bonding, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult) = {
    val currentBalance = gs.accountBalance(tx.sponsor)
    if (currentBalance < tx.value)
      return (gs, TransactionExecutionResult.AccountBalanceInsufficientForTransfer(config.refusedBondingCost, tx.value, currentBalance))

    val (updatedValidatorsBook, queueAppendResult) =
      gs.validatorsBook.addBondingReqToWaitingQueue(
        validator = tx.validator,
        account = tx.sponsor,
        amount = tx.value,
        requestTime = blockTime,
        config)

    return queueAppendResult match {
      case ValidatorsBook.BondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts.updateBalance(tx.sponsor, tx.value), updatedValidatorsBook), TransactionExecutionResult.Success(config.successfulBondingCost))
      case other =>
        (gs, TransactionExecutionResult.BondingRefused(config.refusedBondingCost, other))
    }
  }

  private def executeUnbonding(gs: GlobalState[MS], tx: Transaction.Unbonding, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult) = {
    val (updatedValidatorsBook, queueAppendResult) =
      gs.validatorsBook.addUnbondingReqToWaitingQueue(
        validator = tx.validator,
        amount = tx.value,
        requestTime = blockTime,
        config)

    return queueAppendResult match {
      case ValidatorsBook.UnbondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts, updatedValidatorsBook), TransactionExecutionResult.Success(config.successfulBondingCost))
      case other =>
        (gs, TransactionExecutionResult.UnbondingRefused(config.refusedBondingCost, other))
    }
  }

  private def executeEquivocationSlashing(gs: GlobalState[MS], tx: Transaction.EquivocationSlashing): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: implement slashing
    ???
  }


}
