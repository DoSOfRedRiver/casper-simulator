package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain._
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.{Hash, RealSha256Digester}

/**
  * Encapsulates block-level execution logic.
  */
class CasperMainchainBlocksExecutor[CS, P, MS](executionEngine: ExecutionEngine[MS, Transaction], config: BlockchainConfig) extends BlocksExecutor[MS, NormalBlock] {

  def executeBlockAsVerifier(preState: GlobalState[MS], block: NormalBlock): (GlobalState[MS], Gas) = this.executeBlockAsCreator(preState, block.pTime, block.id, block.creator, block.transactions)

  def executeBlockAsCreator(preState: GlobalState[MS], pTime: Gas, blockId: BlockId, creator: ValidatorId, transactions: IndexedSeq[Transaction]): (GlobalState[MS], Gas) = {
    //cleanup block rewards queue
    val gs0 = preState.cleanupBlockRewardsQueue(pTime, config.pTimeLimitForClaimingBlockReward)

    //process bonding/unbonding buffers
    val gs1: GlobalState[MS] = gs0.processBondingBuffers(pTime)

    //calculate effective gas price for this block
    val effectiveGasPrice: Ether = transactions.map(t => t.gasPrice).min

    //execute all transactions in the block (sequentially)
    val (gs2, gasBurned): (GlobalState[MS], Gas) = executeTransactions(gs1, pTime, transactions, effectiveGasPrice)

    //do the mathematics of block rewards
    val gs3: GlobalState[MS] = processBlockRewards(gs2, blockId, creator, pTime, gasBurned, effectiveGasPrice)

    //bingo
    return (gs3, gasBurned)
  }

  def calculateBlockId(creator: ValidatorId, parents: IndexedSeq[BlockId], justifications: IndexedSeq[BlockId], transactions: IndexedSeq[Transaction], preStateHash: Hash): BlockId = {
    val digester = new RealSha256Digester
    digester.updateWith(creator)
    digester.updateWith(0x01.toByte)
    for (p <- parents)
      digester.updateWithHash(p)
    digester.updateWith(0x02.toByte)
    for (j <- justifications)
      digester.updateWithHash(j)
    digester.updateWith(0x03.toByte)
    for (tx <- transactions)
      executionEngine.updateDigest(tx, digester)
    digester.updateWith(0x04.toByte)
    digester.updateWithHash(preStateHash)
    return digester.generateHash()
  }

  def generateGenesisId(magicWord: String): BlockId = {
    val digester = new RealSha256Digester
    digester.updateWith(magicWord)
    return digester.generateHash()
  }

  private def executeTransactions(preState: GlobalState[MS], pTime: Gas, transactions: IndexedSeq[Transaction], effectiveGasPrice: Ether): (GlobalState[MS], Gas) =
    transactions.foldLeft((preState, 0L)) { case (acc, transaction) =>
      val (gs, executionResult) = executionEngine.executeTransaction(acc._1, transaction, effectiveGasPrice, pTime)
      println(s"(ptime=$pTime) transaction: $transaction (account,nonce)=(${transaction.sponsor},${transaction.nonce}) txResult=$executionResult")
      //todo: recording / logging of execution result
      (gs, acc._2 + executionResult.gasBurned)
    }

  private def processBlockRewards(gs: GlobalState[MS], blockId: BlockId, creator: ValidatorId, pTime: Gas, gasBurned: Gas, effectiveGasPrice: Ether): GlobalState[MS] = {
    val totalBlockReward: Ether = gasBurned * effectiveGasPrice
    val vbWithRewardsDistributed = gs.validatorsBook.distributeRewardsForJustCreatedBlock(blockId, creator, pTime, totalBlockReward)
    val (resultingValidatorsBook, rewardForTheCreator) = vbWithRewardsDistributed.consumeBlockRewards(creator, pTime, config.pTimeLimitForClaimingBlockReward)
    val accountOfBlockCreator = resultingValidatorsBook.getInfoAbout(creator).account
    return GlobalState(
      memoryState = gs.memoryState,
      accounts = gs.accounts.updateBalance(accountOfBlockCreator, rewardForTheCreator),
      validatorsBook = resultingValidatorsBook
    )
  }

}
