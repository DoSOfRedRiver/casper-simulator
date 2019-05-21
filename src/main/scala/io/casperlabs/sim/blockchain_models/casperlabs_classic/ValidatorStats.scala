package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.{Transaction, TransactionExecutionResult}

/**
  * Maintains basic statistics of a validator.
  */
class ValidatorStats {
  private var cNumberOfBlocksReceived = 0
  private var cNumberOfBlocksPublished = 0
  private var cNumberOfSuccessfulTransactionsProcessed = 0
  private var cNumberOfFailedTransactionsProcessed = 0
  private var cNumberOfDeploysPublished = 0
  private var cNumberOfDeploysDiscardedForFatalErrors = 0
  private var cPDagLevel: Long = 0

  def blockReceived(block: NormalBlock): Unit = {
    cNumberOfBlocksReceived += 1
    this.updateTransactionCounters(block)
    this.updateDagLevel(block)
  }

  def blockPublished(block: NormalBlock): Unit = {
    cNumberOfBlocksPublished += 1
    this.updateTransactionCounters(block)
    this.updateDagLevel(block)
    cNumberOfDeploysPublished += block.transactions.size
  }

  def deploysDiscarded(deploys: Iterable[Transaction]): Unit = {
    cNumberOfDeploysDiscardedForFatalErrors += deploys.size
  }

  def numberOfBlocksReceived: Long = cNumberOfBlocksReceived

  def numberOfBlocksPublished: Long = cNumberOfBlocksPublished

  def numberOfSuccessfulTransactionsProcessed: Long = cNumberOfSuccessfulTransactionsProcessed

  def numberOfFailedTransactionsProcessed: Long = cNumberOfFailedTransactionsProcessed

  def numberOfDeploysPublished: Long = cNumberOfDeploysPublished

  def numberOfDeploysDiscardedForFatalErrors: Long = cNumberOfDeploysDiscardedForFatalErrors

  def pDagLevel: Long = cPDagLevel

  private def updateTransactionCounters(block: NormalBlock): Unit = {
    for (tx <- block.executionResults) {
      if (tx.isInstanceOf[TransactionExecutionResult.Success])
        cNumberOfSuccessfulTransactionsProcessed += 1
      else
        cNumberOfFailedTransactionsProcessed += 1
    }
  }

  private def updateDagLevel(block: Block): Unit = {
    cPDagLevel = math.max(cPDagLevel, block.dagLevel)
  }

}
