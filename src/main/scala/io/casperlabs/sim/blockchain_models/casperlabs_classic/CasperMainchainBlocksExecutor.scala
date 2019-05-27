package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain._
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.hashing.{Hash, RealSha256Digester}

import scala.collection.mutable

/**
  * Encapsulates block-level execution logic.
  */
class CasperMainchainBlocksExecutor[CS, P, MS](executionEngine: ExecutionEngine[MS, Transaction], config: BlockchainConfig) extends BlocksExecutor[MS, NormalBlock] {

  def executeBlock(preState: GlobalState[MS], block: NormalBlock): (GlobalState[MS], Gas, Map[Transaction, TransactionExecutionResult]) =
    this.applyBlockToGlobalState(preState, block.pTime, block.positionInPerValidatorChain, block.creator, block.transactions)

  override def createBlock(
                            preState: GlobalState[MS],
                            pTime: Gas,
                            dagLevel: Int,
                            positionInPerValidatorChain: Account,
                            creator: ValidatorId,
                            parents: IndexedSeq[Block],
                            justifications: IndexedSeq[Block],
                            transactions: IndexedSeq[Transaction]): BlocksExecutor.BlockCreationResult[MS] = {

    val (postState, gasBurned, txResults) = this.applyBlockToGlobalState(preState, pTime, positionInPerValidatorChain, creator, transactions)

    //make the block with blank hash
    val blockWithBlankId = NormalBlock(
      id = Block.blankHash,
      creator,
      dagLevel,
      positionInPerValidatorChain,
      parents,
      justifications,
      transactions,
      transactions.map(t => txResults(t)),
      pTime,
      gasBurned,
      weightsMap = postState.validatorsBook.validatorWeightsMap,
      preStateHash = executionEngine.globalStateHash(preState),
      postStateHash = executionEngine.globalStateHash(postState)
    )

    //re-create the block, now with the proper hash value filled-in
    val block = NormalBlock(
      id = this.calculateBlockHash(blockWithBlankId),
      blockWithBlankId.creator,
      blockWithBlankId.dagLevel,
      blockWithBlankId.positionInPerValidatorChain,
      blockWithBlankId.parents,
      blockWithBlankId.justifications,
      blockWithBlankId.transactions,
      blockWithBlankId.executionResults,
      blockWithBlankId.pTime,
      blockWithBlankId.gasBurned,
      blockWithBlankId.weightsMap,
      blockWithBlankId.preStateHash,
      blockWithBlankId.postStateHash
    )

    //we are good
    return BlocksExecutor.BlockCreationResult(block, postState, txResults)
  }

  private def calculateBlockHash(block: NormalBlock): BlockId = {
    val digester = new RealSha256Digester

    digester.updateWith(block.creator)
    digester.updateWith(0x01.toByte)

    digester.updateWith(block.dagLevel)
    digester.updateWith(0x02.toByte)

    digester.updateWith(block.positionInPerValidatorChain)
    digester.updateWith(0x03.toByte)

    for (p <- block.parents)
      digester.updateWith(p.id)
    digester.updateWith(0x04.toByte)

    for (j <- block.justifications)
      digester.updateWith(j.id)
    digester.updateWith(0x05.toByte)

    for (tx <- block.transactions)
      executionEngine.updateDigest(tx, digester)
    digester.updateWith(0x06.toByte)

    for (r <- block.executionResults) {
      digester.updateWith(r.getClass.getSimpleName)
      digester.updateWith(r.gasBurned)
    }
    digester.updateWith(0x07.toByte)

    digester.updateWith(block.pTime)
    digester.updateWith(0x08.toByte)

    digester.updateWith(block.gasBurned)
    digester.updateWith(0x09.toByte)

    for (entry <- block.weightsMap) {
      digester.updateWith(entry._1)
      digester.updateWith(entry._2)
    }
    digester.updateWith(0x0A.toByte)

    digester.updateWith(block.preStateHash)
    digester.updateWith(0x0B.toByte)

    digester.updateWith(block.postStateHash)
    digester.updateWith(0x0C.toByte)

    return digester.generateHash()
  }

  def generateGenesisId(magicWord: String): BlockId = {
    val digester = new RealSha256Digester
    digester.updateWith(magicWord)
    return digester.generateHash()
  }

  private def applyBlockToGlobalState(
                               preState: GlobalState[MS],
                               pTime: Gas,
                               positionInPerValidatorChain: Account,
                               creator: ValidatorId,
                               transactions: IndexedSeq[Transaction]): (GlobalState[MS], Gas, Map[Transaction, TransactionExecutionResult]) = {

    //cleanup block rewards queue
    val gs0 = preState.cleanupBlockRewardsQueue(pTime, config.pTimeLimitForClaimingBlockReward)

    //process bonding/unbonding buffers
    val gs1: GlobalState[MS] = gs0.processBondingBuffers(pTime)

    //calculate effective gas price for this block
    val effectiveGasPrice: Ether = transactions.map(t => t.gasPrice).min

    //execute all transactions in the block (sequentially)
    val (gs2, gasBurned, txResults): (GlobalState[MS], Gas, Map[Transaction, TransactionExecutionResult]) = executeTransactions(gs1, pTime, transactions, effectiveGasPrice)

    //do the mathematics of block rewards
    val gs3: GlobalState[MS] = processBlockRewards(gs2, positionInPerValidatorChain, creator, pTime, gasBurned, effectiveGasPrice)

    //bingo
    return (gs3, gasBurned, txResults)
  }

  private def executeTransactions(preState: GlobalState[MS],
                                  pTime: Gas,
                                  transactions: IndexedSeq[Transaction],
                                  effectiveGasPrice: Ether): (GlobalState[MS], Gas, Map[Transaction, TransactionExecutionResult]) = {

    var gsTmp: GlobalState[MS] = preState
    var gasCounter: Gas = 0L
    val txResults = new mutable.HashMap[Transaction, TransactionExecutionResult]
    for (tx <- transactions) {
      val (gsAfterTx, txResult) = executionEngine.executeTransaction(gsTmp, tx, effectiveGasPrice, pTime)
      txResults += tx -> txResult
      if (! txResult.isFatal) { //transactions which lead to "fatal" errors are not included in the block
        gsTmp = gsAfterTx
        gasCounter += txResult.gasBurned
      }
    }

    return (gsTmp, gasCounter, txResults.toMap)
  }

  private def processBlockRewards(gs: GlobalState[MS], positionInPerValidatorChain: Int, creator: ValidatorId, pTime: Gas, gasBurned: Gas, effectiveGasPrice: Ether): GlobalState[MS] = {
    val totalBlockReward: Ether = gasBurned * effectiveGasPrice
    val pseudoBlockId = AbstractBlock.PseudoId(creator, positionInPerValidatorChain)
    val vbWithRewardsDistributed = gs.validatorsBook.distributeRewardsForJustCreatedBlock(pseudoBlockId, creator, pTime, totalBlockReward)
    val (resultingValidatorsBook, rewardForTheCreator) = vbWithRewardsDistributed.consumeBlockRewards(creator, pTime, config.pTimeLimitForClaimingBlockReward)
    val accountOfBlockCreator = resultingValidatorsBook.getInfoAbout(creator).account
    return GlobalState(
      memoryState = gs.memoryState,
      accounts = gs.accounts.updateBalance(accountOfBlockCreator, rewardForTheCreator),
      validatorsBook = resultingValidatorsBook
    )
  }

}
