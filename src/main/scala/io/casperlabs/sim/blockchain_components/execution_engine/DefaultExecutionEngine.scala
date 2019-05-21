package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, ExecutionEngine}
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.ValidatorsBook.{BondingQueueAppendResult, UnbondingQueueAppendResult}
import io.casperlabs.sim.blockchain_components.hashing.{CryptographicDigester, Hash, RealSha256Digester}

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
  * @param config blockchain parameters
  * @tparam P (computing space) programs type
  * @tparam MS (computing space) memory states type
  */
class DefaultExecutionEngine[P, MS](config: BlockchainConfig, computingSpace: ComputingSpace[P,MS]) extends ExecutionEngine[MS, Transaction] {

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
    //fatal 1: sponsor account is not existing
    if (! gs.accounts.contains(transaction.sponsor))
      return (gs, TransactionExecutionResult.SponsorAccountUnknown(transaction.sponsor))

    //fatal 2: nonce mismatch
//todo: re-enable nonce mismatch checking after finalization is completed and ClientTrafficGenerator can use it to correctly generate nonces

//    if (gs.accounts.getNonceOf(transaction.sponsor) != transaction.nonce)
//      return (gs, TransactionExecutionResult.NonceMismatch(gs.accounts.getNonceOf(transaction.sponsor), transaction.nonce))

    //fatal 3: sponsor does not have enough ether to cover declared gas limit
    if (gs.accountBalance(transaction.sponsor) < transaction.gasLimit * effectiveGasPrice)
      return (gs, TransactionExecutionResult.GasLimitNotCoveredBySponsorAccountBalance(transaction.gasLimit, transaction.gasLimit * transaction.gasPrice, gs.accountBalance(transaction.sponsor)))

    //no fatal error was detected
    //it means that - whatever happens later - we anyway increase the nonce and we secure the money for the gas
    val gsJustBeforeTransactionTypeSpecificPart = GlobalState[MS](
      memoryState = gs.memoryState,
      accounts = gs.accounts.updateBalanceAndIncreaseNonce(transaction.sponsor, - transaction.gasLimit * effectiveGasPrice),
      validatorsBook = gs.validatorsBook
    )

    //here we execute the per-transaction-type specific part; caution: returned global state does not have transaction cost paid yet !
    val (gsJustAfterTransactionTypeSpecificPart, txResult): (GlobalState[MS], TransactionExecutionResult) = transaction match {
      case tx: Transaction.AccountCreation => this.executeAccountCreation(gsJustBeforeTransactionTypeSpecificPart, tx)
      case tx: Transaction.SmartContractExecution[P,MS] => this.executeSmartContract(gsJustBeforeTransactionTypeSpecificPart, tx)
      case tx: Transaction.EtherTransfer => this.executeEtherTransfer(gsJustBeforeTransactionTypeSpecificPart, tx)
      case tx: Transaction.Bonding => this.executeBonding(gsJustBeforeTransactionTypeSpecificPart, tx, blockTime)
      case tx: Transaction.Unbonding => this.executeUnbonding(gsJustBeforeTransactionTypeSpecificPart, tx, blockTime)
      case tx: Transaction.EquivocationSlashing => this.executeEquivocationSlashing(gsJustBeforeTransactionTypeSpecificPart,tx)
    }

    //if gas limit was exceeded, we ignore any other possible error
    if (txResult.gasBurned > transaction.gasLimit)
      return (gsJustBeforeTransactionTypeSpecificPart, TransactionExecutionResult.GasLimitExceeded(transaction.gasLimit))

    //changes to the global state are retained only for successful transactions
    val effectiveGlobalState = txResult match {
      case TransactionExecutionResult.Success(_) => gsJustAfterTransactionTypeSpecificPart
      case other => gsJustBeforeTransactionTypeSpecificPart
    }

    //we return money for whatever was unused (as compared to declared gas limit)
    val resultGS = effectiveGlobalState.updateAccountBalance(transaction.sponsor, (transaction.gasLimit - txResult.gasBurned) * effectiveGasPrice)
    return (resultGS, txResult)
  }

  /**
    * Calculate hash of given global state.
    *
    * @param gs global state
    * @return Sha-256 hash
    */
  def globalStateHash(gs: GlobalState[MS]): Hash = {
    val digester = new RealSha256Digester
    computingSpace.updateDigestWithMemState(gs.memoryState, digester)
    gs.validatorsBook.updateDigest(digester)
    gs.accounts.updateDigest(digester)
    return digester.generateHash()
  }

  def updateDigest(tx: Transaction, digest: CryptographicDigester): Unit = {
    digest.updateWith(tx.id)
    digest.updateWith(tx.sponsor)
    digest.updateWith(tx.nonce)
    digest.updateWith(tx.gasPrice)
    digest.updateWith(tx.gasLimit)

    tx match {
      case tx: Transaction.AccountCreation =>
        digest.updateWith(0x01.toByte)
        digest.updateWith(tx.newAccount)
      case tx: Transaction.SmartContractExecution[P,MS] =>
        digest.updateWith(0x02.toByte)
        computingSpace.updateDigestWithProgram(tx.program, digest)
      case tx: Transaction.EtherTransfer =>
        digest.updateWith(0x03.toByte)
        digest.updateWith(tx.targetAccount)
        digest.updateWith(tx.value)
      case tx: Transaction.Bonding =>
        digest.updateWith(0x04.toByte)
        digest.updateWith(tx.validator)
        digest.updateWith(tx.value)
      case tx: Transaction.Unbonding =>
        digest.updateWith(0x05.toByte)
        digest.updateWith(tx.validator)
        digest.updateWith(tx.value)
      case tx: Transaction.EquivocationSlashing =>
        digest.updateWith(0x06.toByte)
        digest.updateWith(tx.victim)
        digest.updateWith(tx.evidence._1.bytes)
        digest.updateWith(tx.evidence._2.bytes)
    }
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

  private def executeSmartContract(gs: GlobalState[MS], tx: Transaction.SmartContractExecution[P,MS]): (GlobalState[MS], TransactionExecutionResult) = {
    val programResult: computingSpace.ProgramResult = computingSpace.execute(tx.program, gs.memoryState, tx.gasLimit)

    return programResult match {
      case computingSpace.ProgramResult.Success(newMemoryState, gas) =>
        (GlobalState[MS](newMemoryState, gs.accounts, gs.validatorsBook), TransactionExecutionResult.Success(gas))
      case computingSpace.ProgramResult.Crash(gas) =>
        (gs, TransactionExecutionResult.SmartContractUnhandledException(gas))
      case computingSpace.ProgramResult.GasLimitExceeded =>
        (gs, TransactionExecutionResult.GasLimitExceeded(tx.gasLimit))
    }
  }

  private def executeEtherTransfer(gs: GlobalState[MS], tx: Transaction.EtherTransfer): (GlobalState[MS], TransactionExecutionResult) = {
    if (! gs.accounts.contains(tx.targetAccount))
      return (gs, TransactionExecutionResult.TargetAccountUnknown(config.transferCost, tx.targetAccount))

    if (gs.accountBalance(tx.sponsor) < tx.value)
      return (gs, TransactionExecutionResult.AccountBalanceInsufficientForTransfer(config.transferCost, tx.value, gs.accountBalance(tx.sponsor)))

    return (gs.transfer(tx.sponsor, tx.targetAccount, tx.value), TransactionExecutionResult.Success(config.transferCost))
  }

  private def executeBonding(gs: GlobalState[MS], tx: Transaction.Bonding, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult) = {
    val currentBalance = gs.accountBalance(tx.sponsor)
    if (currentBalance < tx.value)
      return (gs, TransactionExecutionResult.AccountBalanceInsufficientForTransfer(config.refusedBondingCost, tx.value, currentBalance))

    if (gs.validatorsBook.isRegistered(tx.validator)) {
      val ownerAccount = gs.validatorsBook.getInfoAbout(tx.validator).account
      if (tx.sponsor != ownerAccount)
        return (gs, TransactionExecutionResult.BondingRefused(config.refusedBondingCost, BondingQueueAppendResult.AccountMismatch))
    }

    val (updatedValidatorsBook, queueAppendResult) =
      gs.validatorsBook.addBondingReqToWaitingQueue(
        validator = tx.validator,
        account = tx.sponsor,
        amount = tx.value,
        requestTime = blockTime,
        config)

    return queueAppendResult match {
      case ValidatorsBook.BondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts.updateBalance(tx.sponsor, - tx.value), updatedValidatorsBook), TransactionExecutionResult.Success(config.successfulBondingCost))
      case other =>
        (gs, TransactionExecutionResult.BondingRefused(config.refusedBondingCost, other))
    }
  }

  private def executeUnbonding(gs: GlobalState[MS], tx: Transaction.Unbonding, blockTime: Gas): (GlobalState[MS], TransactionExecutionResult) = {
    if (gs.validatorsBook.isRegistered(tx.validator)) {
      val info = gs.validatorsBook.getInfoAbout(tx.validator)
      if (info.stake == 0)
        return (gs, TransactionExecutionResult.UnbondingRefused(config.refusedUnbondingCost, UnbondingQueueAppendResult.ValidatorNotActive))
      if (tx.sponsor != info.account)
        return (gs, TransactionExecutionResult.UnbondingRefused(config.refusedUnbondingCost, UnbondingQueueAppendResult.AccountMismatch))
    } else {
      return (gs, TransactionExecutionResult.UnbondingRefused(config.refusedUnbondingCost, UnbondingQueueAppendResult.ValidatorNotActive))
    }

    val (updatedValidatorsBook, queueAppendResult) =
      gs.validatorsBook.addUnbondingReqToWaitingQueue(
        validator = tx.validator,
        amount = tx.value,
        requestTime = blockTime,
        config)

    return queueAppendResult match {
      case ValidatorsBook.UnbondingQueueAppendResult.OK =>
        (GlobalState(gs.memoryState, gs.accounts, updatedValidatorsBook), TransactionExecutionResult.Success(config.successfulUnbondingCost))
      case other =>
        (gs, TransactionExecutionResult.UnbondingRefused(config.refusedUnbondingCost, other))
    }
  }

  private def executeEquivocationSlashing(gs: GlobalState[MS], tx: Transaction.EquivocationSlashing): (GlobalState[MS], TransactionExecutionResult) = {
    //todo: implement slashing
    ???
  }


}
