package io.casperlabs.sim.blockchain_components.execution_engine

/**
  * Encapsulation of results of running a transactions by the execution engine.
  */
sealed abstract class TransactionExecutionResult {

  /**
    * Gas that was burned by execution of the transaction.
    */
  def gasBurned: Gas

  /**
    * Fatal errors denote situations where a transaction cannot be included in the blockchain.
    * Usually because nobody would pay for such inclusion.
    */
  def isFatal: Boolean

}

object TransactionExecutionResult {

  case class Success(gasBurned: Gas) extends TransactionExecutionResult {
    override def isFatal: Boolean = false
  }

  case class SponsorAccountUnknown(account: Account) extends TransactionExecutionResult {
    override def gasBurned: Gas = 0
    override def isFatal: Boolean = true
  }

  case class NonceMismatch(currentNonce: Long, nonceDeclaredInDeploy: Long) extends TransactionExecutionResult {
    override def gasBurned: Gas = 0
    override def isFatal: Boolean = true
  }

  case class GasLimitNotCoveredBySponsorAccountBalance(gasLimit: Gas, cost: Ether, sponsorBalance: Ether) extends TransactionExecutionResult {
    override def isFatal: Boolean = true
    override def gasBurned: Gas = 0
  }

  case class GasLimitExceeded(gasLimit: Gas) extends TransactionExecutionResult {
    override def gasBurned: Gas = gasLimit
    override def isFatal: Boolean = false
  }

  case class AccountBalanceInsufficientForTransfer(gasBurned: Gas, requestedAmount: Ether, currentBalance: Ether) extends TransactionExecutionResult {
    override def isFatal: Boolean = false
  }

  case class SmartContractUnhandledException(gasBurned: Gas) extends TransactionExecutionResult {
    override def isFatal: Boolean = false
  }

  case class BondingRefused(gasBurned: Gas, reason: ValidatorsBook.BondingQueueAppendResult) extends TransactionExecutionResult {
    override def isFatal: Boolean = false
  }

  case class UnbondingRefused(gasBurned: Gas, reason: ValidatorsBook.UnbondingQueueAppendResult) extends TransactionExecutionResult {
    override def isFatal: Boolean = false
  }

}
