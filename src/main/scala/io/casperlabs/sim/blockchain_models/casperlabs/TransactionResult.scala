package io.casperlabs.sim.blockchain_models.casperlabs

sealed abstract class TransactionResult {
}

object TransactionResult {
  case object Success extends TransactionResult
  case class NonceMismatch(currentNonce: Long, nonceDeclaredInDeploy: Long) extends TransactionResult
  case class GasLimitNotCoveredBySponsorAccountBalance(gasLimit: Gas, cost: Ether, sponsorBalance: Ether) extends TransactionResult
  case class GasLimitExceeded(gasLimit: Gas, gasActuallyUsed: Gas) extends TransactionResult
  case class AccountBalanceInsufficient(currentBalance: Ether) extends TransactionResult
  case class UnbondingOverdrive(actualStake: Ether, requestedValue: Ether) extends TransactionResult
}
