package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, BlocksExecutor, ExecutionEngine}
import io.casperlabs.sim.blockchain_components.execution_engine._

/**
  * Encapsulates block-level execution logic.
  */
class CasperMainchainBlocksExecutor[CS, P, MS](executionEngine: ExecutionEngine[MS], config: BlockchainConfig) extends BlocksExecutor[MS] {

  def executeBlock(preState: GlobalState[MS], block: Block): (GlobalState[MS], Gas) = {
    //cleanup block rewards queue
    val gs0 = preState.cleanupBlockRewardsQueue(block.pTime, config.pTimeLimitForClaimingBlockReward)

    //process bonding/unbonding buffers
    val gs1: GlobalState[MS] = gs0.processBondingBuffers(block.pTime)

    //calculate effective gas price for this block
    val effectiveGasPrice: Ether = block.transactions.map(t => t.gasPrice).min

    //execute all transactions in the block (sequentially)
    val (gs2, gasBurned): (GlobalState[MS], Gas) = executeTransactions(gs1, block, effectiveGasPrice)

    //do the mathematics of block rewards
    val gs3: GlobalState[MS] = processBlockRewards(gs2, block, gasBurned, effectiveGasPrice)

    //bingo
    return (gs3, gasBurned)
  }

  private def executeTransactions(preState: GlobalState[MS], block: Block, effectiveGasPrice: Ether): (GlobalState[MS], Gas) =
    block.transactions.foldLeft((preState, 0L)) { case (acc, transaction) =>
      val (gs, executionResult) = executionEngine.executeTransaction(acc._1, transaction, effectiveGasPrice, block.pTime)
      //todo: recording / logging of execution result
      (gs, acc._2 + executionResult.gasBurned)
    }

  private def processBlockRewards(gs: GlobalState[MS], block: Block, gasBurned: Gas, effectiveGasPrice: Ether): GlobalState[MS] = {
    val totalBlockReward: Ether = gasBurned * effectiveGasPrice
    val vbWithRewardsDistributed = gs.validatorsBook.distributeRewardsForJustCreatedBlock(block.id, block.creator, block.pTime, totalBlockReward)
    val (resultingValidatorsBook, rewardForTheCreator) = vbWithRewardsDistributed.consumeBlockRewards(block.creator, block.pTime, config.pTimeLimitForClaimingBlockReward)
    val accountOfBlockCreator = resultingValidatorsBook.getInfoAbout(block.creator).account
    return GlobalState(
      memoryState = gs.memoryState,
      accounts = gs.accounts.updateBalance(accountOfBlockCreator, rewardForTheCreator),
      validatorsBook = resultingValidatorsBook
    )
  }

}
