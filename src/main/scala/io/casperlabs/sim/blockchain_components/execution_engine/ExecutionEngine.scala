package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace

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
class ExecutionEngine[CS <: ComputingSpace[P, MS], P, MS](config: BlockchainConfig, computingSpace: CS) {

  /**
    * Executes sequence of transaction as a block.
    * Caution: this is needed because we have block-level logic and transaction-level logic.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transactions sequence of transactions
    * @creator the validator who created the block
    */
  def executeBlock(gs: GlobalState[MS], transactions: Iterable[Transaction], creator: ValidatorId): GlobalState[MS] = {
    var gsTmp: GlobalState[MS] = gs

    //phase 1: process bonding/unbonding buffers
    val (updatedValidatorsBook, unbondingTransfers) = gs.validatorsBook.processQueueAccordingToCurrentTime(gs.pTime)
    gsTmp = GlobalState(gs.memoryState, gs.accounts, updatedValidatorsBook, gs.pTime)
    for ((validatorId, etherAmount) <- unbondingTransfers)
      gsTmp = gs.updateAccountBalance(gsTmp.validatorsBook.getInfoAbout(validatorId).account, etherAmount)

    //phase 2: find effective gas price
    val effectiveGasPrice: Ether = transactions.map(t => t.gasPrice).min

    //phase 3: execute transactions
    var tmpGS: GlobalState[MS] = gs
    var gasCounter: Gas = 0
    for (transaction <- transactions) {
      val (resultingGS, executionResult) = this.executeTransaction(tmpGS, transaction, effectiveGasPrice)
      //todo: recording of execution result goes here
      tmpGS = resultingGS
      gasCounter += executionResult.gasBurned
    }

    //phase 4: update block rewards buffer
    val etherToBeDistributed: Ether = gasCounter * effectiveGasPrice
    val numberOfValidators: Int = gsTmp.numberOfActiveValidators
    val profitPerValidator: Ether = etherToBeDistributed / numberOfValidators
    gsTmp = GlobalState(tmpGS.memoryState, tmpGS.accounts, tmpGS.validatorsBook.storeBlockReward(profitPerValidator, gsTmp.pTime), tmpGS.pTime)

    //phase 5: pay block rewards to the creator of this block
    val (vBookAfterCreatorPremiumStoreReset, reward) = gsTmp.validatorsBook.consumeBlockRewards(creator, gs.pTime)
    val profitRemainder: Ether = etherToBeDistributed % numberOfValidators
    val creatorAccount: Account = gsTmp.validatorsBook.getInfoAbout(creator).account
    val accountsAfterRewardPayment = gsTmp.accounts.updateBalance(creatorAccount, reward + profitRemainder)
    gsTmp = new GlobalState[MS](gsTmp.memoryState, accountsAfterRewardPayment, vBookAfterCreatorPremiumStoreReset, gsTmp.pTime)

    //phase 6: update p-time
    return new GlobalState[MS](gsTmp.memoryState, gsTmp.accounts, gsTmp.validatorsBook, gsTmp.pTime + gasCounter)
  }

  /**
    * Executes transaction against given global state, producing a new global state.
    *
    * @param gs global state snapshot at the moment before the transaction is executed
    * @param transaction transaction to be executed
    * @return a pair (resulting global state, execution status)
    */
  def executeTransaction(gs: GlobalState[MS], transaction: Transaction, gasPrice: Ether): (GlobalState[MS], TransactionExecutionResult) = {
    //should be checked earlier; if the check fails here then we apparently have a bug
    assert(gs.accounts.contains(transaction.sponsor))

    //nonce mismatch forces quick break; no gas will be consumed from sponsor account
    if (gs.accounts.getNonceOf(transaction.sponsor) != transaction.nonce)
      return (gs, TransactionExecutionResult.NonceMismatch(gasBurned = 0, gs.accounts.getNonceOf(transaction.sponsor), transaction.nonce))

    //before the transaction executing we require that sponsor has enough money to cover the maximum cost of transaction he declared to cover
    if (gs.accountBalance(transaction.sponsor) < transaction.gasLimit * transaction.gasPrice)
      return (gs, TransactionExecutionResult.GasLimitNotCoveredBySponsorAccountBalance(gasBurned = 0, transaction.gasLimit, transaction.gasLimit * transaction.gasPrice, gs.accountBalance(transaction.sponsor)))

    //here we execute the per-transaction-type specific part; caution: returned global state does not have transaction cost paid yet !
    val (gsAfterTransactionExecution, txResult): (GlobalState[MS], TransactionExecutionResult) = transaction match {
      case tx: Transaction.AccountCreation => this.executeAccountCreation(gs, tx)
      case tx: Transaction.SmartContractExecution[CS,P,MS] => this.executeSmartContract(gs, tx)
      case tx: Transaction.EtherTransfer => this.executeEtherTransfer(gs, tx)
      case tx: Transaction.Bonding => this.executeBonding(gs, tx)
      case tx: Transaction.Unbonding => this.executeUnbonding(gs, tx)
      case tx: Transaction.EquivocationSlashing => this.executeEquivocationSlashing(gs,tx)
    }

    //now we know what the actual transaction cost (= gas burned) was
    //so we are able to calculate new user account state (nonce and ether must be updated)
    val gasCappedByGasLimit: Gas = math.min(txResult.gasBurned, transaction.gasLimit) * transaction.gasPrice

    //todo: add proper rollback behaviour here (when sponsor has not enough ether left to cover transaction cost, we need to rollback stuff and only take the payment from his account and increase nonce).

    //if gas limit was exceeded then memory state will not be updated (but the nonce and account's ether will be updated anyway !)
    val updatedGS = GlobalState[MS](
        memoryState = if (txResult.gasBurned > transaction.gasLimit) gs.memoryState else gsAfterTransactionExecution.memoryState,
        accounts = gsAfterTransactionExecution.accounts.updateBalanceAndIncreaseNonce(transaction.sponsor, - gasCappedByGasLimit),
        validatorsBook = gsAfterTransactionExecution.validatorsBook,
        pTime = gsAfterTransactionExecution.pTime + gasCappedByGasLimit
      )

    return (updatedGS, txResult)
  }

//################################################ PER-TRANSACTION-TYPE SPECIFIC PARTS #####################################################################

  private def executeAccountCreation(gs: GlobalState[MS], tx: Transaction.AccountCreation): (GlobalState[MS], TransactionExecutionResult) = {
    assert(! gs.accounts.contains(tx.newAccount))
    val updatedGS = GlobalState(
      memoryState = gs.memoryState,
      accounts = gs.accounts.addAccount(tx.newAccount),
      validatorsBook = gs.validatorsBook,
      pTime = gs.pTime)
    return (updatedGS, TransactionExecutionResult.Success(config.accountCreationCost))
  }

  private def executeSmartContract(gs: GlobalState[MS], tx: Transaction.SmartContractExecution[CS,P,MS]): (GlobalState[MS], TransactionExecutionResult) = {
    val programResult: computingSpace.ProgramResult = computingSpace.execute(tx.program, gs.memoryState)

    return programResult.ms match {
      case Some(newMemoryState) => (GlobalState[MS](newMemoryState, gs.accounts, gs.validatorsBook, gs.pTime), TransactionExecutionResult.Success(programResult.gasUsed))
      case None => (gs, TransactionExecutionResult.SmartContractUnhandledException(programResult.gasUsed))
    }
  }

  private def executeEtherTransfer(gs: GlobalState[MS], tx: Transaction.EtherTransfer): (GlobalState[MS], TransactionExecutionResult) = {
    return if (gs.accountBalance(tx.sponsor) >= tx.value)
      (gs.transfer(tx.sponsor, tx.targetAccount, tx.value), TransactionExecutionResult.Success(config.transferCost))
    else
      (gs, TransactionExecutionResult.AccountBalanceInsufficientForTransfer(config.transferCost, tx.value, gs.accountBalance(tx.sponsor)))
  }

  private def executeBonding(gs: GlobalState[MS], tx: Transaction.Bonding): (GlobalState[MS], TransactionExecutionResult) = {
    val (updatedValidatorsBook, queueAppendResult) = gs.validatorsBook.addBondingReqToWaitingQueue(tx.validator, tx.value, gs.pTime, config)

    return queueAppendResult match {
      case ValidatorsBook.BondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts, updatedValidatorsBook, gs.pTime), TransactionExecutionResult.Success(config.successfulBondingCost))
      case other =>
        (gs, TransactionExecutionResult.BondingRefused(config.refusedBondingCost, other))
    }
  }

  private def executeUnbonding(gs: GlobalState[MS], tx: Transaction.Unbonding): (GlobalState[MS], TransactionExecutionResult) = {
    val (updatedValidatorsBook, queueAppendResult) = gs.validatorsBook.addUnbondingReqToWaitingQueue(tx.validator, tx.value, gs.pTime, config)

    return queueAppendResult match {
      case ValidatorsBook.BondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts, updatedValidatorsBook, gs.pTime), TransactionExecutionResult.Success(config.successfulBondingCost))
      case other =>
        (gs, TransactionExecutionResult.BondingRefused(config.refusedBondingCost, other))
    }
  }

  private def executeEquivocationSlashing(gs: GlobalState[MS], tx: Transaction.EquivocationSlashing): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: implement slashing
    ???
  }


}
