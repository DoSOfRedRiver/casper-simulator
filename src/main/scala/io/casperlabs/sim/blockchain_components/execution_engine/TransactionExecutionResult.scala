package io.casperlabs.sim.blockchain_components.execution_engine

/**
  * Encapsulation of results of running a transactions by the execution engine.
  */
sealed abstract class TransactionExecutionResult {

  /**
    * Gas that was burned by execution of the transaction.
    */
  def gasBurned: Gas

}

object TransactionExecutionResult {
  case class Success(gasBurned: Gas) extends TransactionExecutionResult
  case class NonceMismatch(gasBurned: Gas, currentNonce: Long, nonceDeclaredInDeploy: Long) extends TransactionExecutionResult
  case class GasLimitNotCoveredBySponsorAccountBalance(gasBurned: Gas, gasLimit: Gas, cost: Ether, sponsorBalance: Ether) extends TransactionExecutionResult
  case class GasLimitExceeded(gasBurned: Gas, gasLimit: Gas) extends TransactionExecutionResult
  case class AccountBalanceInsufficient(gasBurned: Gas, currentBalance: Ether) extends TransactionExecutionResult
  case class UnbondingOverdrive(gasBurned: Gas, actualStake: Ether, requestedValue: Ether) extends TransactionExecutionResult
}
