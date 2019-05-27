package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, BlocksExecutor, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.blockchain_components.graphs.DoublyLinkedDag.InsertResult
import io.casperlabs.sim.blockchain_components.graphs.{DoublyLinkedDag, IndexedTwoArgRelation}
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_components.pretty_printing.{BlockdagPrettyPrinter, BlocksBufferPrettyPrinter}
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Validator.{AddBlockResult, ProposeTik}
import io.casperlabs.sim.simulation_framework._
import org.slf4j.LoggerFactory

import scala.collection.mutable.ArrayBuffer

/**
  * Agent plugin that encapsulates validator's main logic:
  *   1. validating incoming blocks
  *   2. maintaining local copy of the blockdag
  *   3. creating new blocks and publishing them (i.e. participating in consensus protocol).
  *
  * @param config
  * @param genesisBlock
  * @param gossipService
  */
class Validator(
                 blockchainConfig: BlockchainConfig,
                 validatorId: ValidatorId,
                 genesisBlock: Genesis,
                 genesisGlobalState: GlobalState[BinaryArraySpace.MemoryState],
                 gossipService: Gossip[NodeId, AgentRef],
                 proposeDelay: TimeDelta,
                 globalStatesStorage: GlobalStatesStorage[BinaryArraySpace.MemoryState, Transaction]
               ) extends PluggableAgentBehaviour {

  private val log = LoggerFactory.getLogger(s"validator-$validatorId")

  type MS = BinaryArraySpace.MemoryState
  type P = BinaryArraySpace.Program
  type CS = ComputingSpace[P, MS]
  type GS = GlobalState[MS]

  //transactions arrived from clients, but not yet published in blocks
  //caution: we use ArrayBuffer here so to retain the ordering of incoming transactions, so the inter-transaction dependencies (if any) are properly processed
  //todo: consider auto sorting by nonce (per sponsor account) on block creation
  private val deployBuffer: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]

  //buffer of blocks we failed to integrate with local blockdag because of missing justifications
  //this is represented as 2-arg relation; entry (a,b) in this relation means "block a is missing a justification b"
  //hence blockBuffer.sources is the collection of blocks that are waiting (received but not yet integrated into the local blockdag)
  private val blocksBuffer: IndexedTwoArgRelation[NormalBlock,NormalBlock] = new IndexedTwoArgRelation[NormalBlock,NormalBlock]

  // TODO: share DAG structure among nodes
  //parent-child dag
  private val pDag: DoublyLinkedDag[Block] = DoublyLinkedDag.pBlockDag(genesisBlock)

  //justifications DAG
  private val jDag: DoublyLinkedDag[Block] = DoublyLinkedDag.jBlockDag(genesisBlock)

  //latest block I created and published (if any)
  private var myOwnLatestBlock: Option[NormalBlock] = None

  // TODO: do this without a var
  private var lastProposeTime: Timepoint = Timepoint(0L)

  //we use BinaryArraySpace as our computing space //todo: make this configurable ?
  private val computingSpace: CS = BinaryArraySpace.ComputingSpace

  //execution engine in use //todo: make this configurable ?
  private val executionEngine = new DefaultExecutionEngine(blockchainConfig, computingSpace)

  //blocks executor in use //todo: make this configurable ?
  private val blocksExecutor = new CasperMainchainBlocksExecutor[CS, P, MS](executionEngine, blockchainConfig)

  //basic statistics we maintain just in case they are needed (regardless of any more sophisticated stats done via external data science tooling)
  private val stats = new ValidatorStats

//###################################### PLUGIN CALLBACKS ########################################

  override def startup(): Unit = {
    log.info(s"${context.timeOfCurrentEvent}: startup of ${context.selfLabel}")
    globalStatesStorage.store(genesisGlobalState)
    thisAgent.setTimerEvent(proposeDelay, ProposeTik)
  }

  override def shutdown(): Unit = {
    log.info(s"${context.timeOfCurrentEvent}: ################# shutdown of ${context.selfLabel} #################")
    log.info(s"    blocks received: ${stats.numberOfBlocksReceived}")
    log.info(s"    blocks published: ${stats.numberOfBlocksPublished}")
    log.info(s"    transactions processed (successful): ${stats.numberOfSuccessfulTransactionsProcessed}")
    log.info(s"    transactions processed (errors): ${stats.numberOfFailedTransactionsProcessed}")
    log.info(s"    deploys published: ${stats.numberOfDeploysPublished}")
    log.info(s"    deploys discarded for fatal errors: ${stats.numberOfDeploysDiscardedForFatalErrors}")
    log.info(s"    pDag depth: ${stats.pDagLevel}")
    log.info(s"    blocks currently waiting in the buffer: ${blocksBuffer.sources.size}")
    log.info(s"    max number of blocks waiting in the buffer: ${stats.maxNumberOfBlocksInTheWaitingBuffer}")
    log.info(s"    deploys waiting in the buffer: ${deployBuffer.size}")
  }

  override def onExternalEvent(msg: Any): Boolean =
    consumeIfMatched(msg) {
      case tx: Transaction =>
        log.debug(s"${context.timeOfCurrentEvent}: received deploy $msg")
        deployBuffer += tx
    }

  override def onTimer(msg: Any): Boolean =
    consumeIfMatched(msg) {
      case ProposeTik =>
        val (shouldProposeNewBlockNow, delayToNextWakeUp) = this.decideIfWeWantToProposeBlockNowAndCalculateDelayToNextWakeup()
          if (shouldProposeNewBlockNow) {
            lastProposeTime = thisAgent.timeOfCurrentEvent
            this.createAndPublishNewBlock()
          } else {
            log.debug(s"${context.timeOfCurrentEvent}: giving up with block creation at current wake-up")
          }
        thisAgent.setTimerEvent(delayToNextWakeUp, ProposeTik)
    }

  override def receive(sender: AgentRef, msg: Any): Boolean =
    consumeIfMatched(msg) {
      case block: NormalBlock => handleIncomingBlock(block)
    }

//#################################################################################################

  private def handleIncomingBlock(block: NormalBlock): Unit = {
    if (log.isTraceEnabled) {
      val blocksBufferDump = if (blocksBuffer.isEmpty) "[empty]" else "\n" + BlocksBufferPrettyPrinter.print(blocksBuffer)
      log.trace(s"${context.timeOfCurrentEvent}: received block $block, current blocks buffer: \n$blocksBufferDump")
      log.trace(s"${context.timeOfCurrentEvent}: current p-DAG: \n${BlockdagPrettyPrinter.print(pDag)} ")
    }

    stats.blockWasReceived(block)

    validateIncomingBlockAndAttemptAddingItToLocalBlockdag(block) match {
      case AddBlockResult.AlreadyAdded =>
        // We got this block already, nothing to do
        log.trace("incoming block case: already added")

      case AddBlockResult.MissingJustifications(justifications) =>
        for (j <- justifications)
          blocksBuffer.addPair(block, j.asInstanceOf[NormalBlock]) //this casting is legal because Genesis block is always present (hence cannot be missing and registered as missing dependency)
        //todo: add block buffer purging here (if a block stays in the buffer for too long, it should be discarded because we assume it must be a side-effect of hacking attempt)
        log.trace(s"incoming block case: missing justifications: ${justifications.map(_.shortId).mkString(",")}, updated blocks buffer to: \n${BlocksBufferPrettyPrinter.print(blocksBuffer)}")

      case AddBlockResult.Invalid =>
        // Something is wrong with the block.
        // No new messages need to be sent.
        // TODO: slashing
        log.warn(s"received block recognized as invalid: $block")

      case AddBlockResult.Valid =>
        // Block is valid, gossip to others
        //todo: this does not work correctly with current mock of gossiping (clarify/fix this !)
        //gossipService.gossip(Node.Comm.NewBlock(b))
        log.trace("incoming block case: valid")

        //possibly this new block unlocked some other blocks in the blocks buffer, which we now can add to the blockdag as well
        //this phenomenon causes possibly a whole collection of blocks being added in a "cascaded" process
        this.runBufferUnlockingCascadeFor(block)
        log.trace(s"final pDag: \n${BlockdagPrettyPrinter.print(pDag)}")
    }

    log.trace(s"final blocks buffer: \n${BlocksBufferPrettyPrinter.print(blocksBuffer)}")
    stats.blocksBufferSizeIs(blocksBuffer.sources.size)
  }

  //one block arriving may unlock other block received previously, that were waiting for missing justifications
  //this leads to cascaded "unlocking" process, we have to run in a loop until no more unlocking is possible
  private def runBufferUnlockingCascadeFor(blockJustAddedToTheLocalBlockdag: NormalBlock): Unit = {
    val blocksWaitingForThisJustification = blocksBuffer.findSourcesFor(blockJustAddedToTheLocalBlockdag)
    blocksBuffer.removeTarget(blockJustAddedToTheLocalBlockdag)
    for (block <- blocksWaitingForThisJustification if ! blocksBuffer.hasSource(block)) {
      val result = this.validateIncomingBlockAndAttemptAddingItToLocalBlockdag(block)
      assert (result == AddBlockResult.Valid) //must be valid because we already added all justifications to the blockdag
      runBufferUnlockingCascadeFor(block)
    }
  }

  private def validateIncomingBlockAndAttemptAddingItToLocalBlockdag(b: NormalBlock): AddBlockResult =
    (pDag.insert(b, b.parents), jDag.insert(b, b.justifications)) match {
      case (DoublyLinkedDag.InsertResult.Success(pInsert), DoublyLinkedDag.InsertResult.Success(jInsert)) =>
        if (validateIncomingBlock(b)) {
          pInsert()
          jInsert()
          AddBlockResult.Valid
        } else {
          AddBlockResult.Invalid
        }

      case (_, DoublyLinkedDag.InsertResult.MissingTargets(jMissing)) =>
        // We assume that parents are a sub-set of justifications and
        // therefore only check the justifications.
        // TODO: Confirm this assumption and call block invalid otherwise
        AddBlockResult.MissingJustifications(jMissing.toIndexedSeq)

      case (_, DoublyLinkedDag.InsertResult.AlreadyInserted()) =>
        AddBlockResult.AlreadyAdded

      case (_, DoublyLinkedDag.InsertResult.Success(_)) =>
        throw new RuntimeException("Unreachable state")
    }

  //due to simplifications done in the simulator (and the fact that we do not have to defend against our own source code),
  //the validation of incoming blocks done here is quite limited (as compared to the real implementation of the blockchain)
  private def validateIncomingBlock(block: NormalBlock): Boolean = {
    if (block.parents(0).postStateHash != block.preStateHash) {
      log.debug(s"block ${block.shortId}: pre-state hash mismatch")
      return false
    }

    globalStatesStorage.read(block.preStateHash) match {
      case Some(preState) =>
        val (gs, gas, txResults) = blocksExecutor.executeBlock(preState, block)
        val postStateHash = executionEngine.globalStateHash(gs)
        if (postStateHash == block.postStateHash && gas == block.gasBurned) {
          globalStatesStorage.store(gs)
          return true
        } else {
          log.debug(s"block ${block.shortId}: post-state mismatch")
          return false
        }
      case None =>
        //if this happens then we have a bug; successful insertion in pDAG implies that we (should) have seen and validated parents before
        throw new RuntimeException(s"pre-state of block ${block.shortId} was not found in storage")
    }
  }

  private def decideIfWeWantToProposeBlockNowAndCalculateDelayToNextWakeup(): (Boolean, TimeDelta) = {
    //todo: replace this method with proper abstraction of blocks proposing strategy (should this be another PluggableAgentBehaviour ?)
    val diff = thisAgent.timeOfCurrentEvent - lastProposeTime
    (diff >= proposeDelay && deployBuffer.nonEmpty, proposeDelay)
  }

  private[this] def createAndPublishNewBlock(): Unit = {
    // TODO: check for equivocations?
    //todo: implement merging or parents; for now we implement block-tree only

    log.trace(s"${context.timeOfCurrentEvent}: about to create new block, current pDAG is: \n ${BlockdagPrettyPrinter.print(pDag)}")

    //run fork-choice; in this variant of consensus we select only one parent (hence the blockdag is a tree)
    val latestMessages: Map[ValidatorId, Block] = (jDag.tips.groupBy(_.creator) - Block.psuedoValidatorIdUsedForGenesisBlock).mapValues(_.head)
    val selectedParentBlock = BlockdagUtils.lmdMainchainGhost[ValidatorId, Block, Hash](
      latestMessages,
      validatorWeightExtractor = (block, vid) => block.weightsMap.getOrElse(vid, 0L),
      pDag,
      genesisBlock,
      tieBreaker = block => block.id
    )

    log.debug(s"${context.timeOfCurrentEvent}: fork choice result = ${selectedParentBlock.shortId}")

    //take the post-state hash of the selected to-become-parent block
    val preStateHash: Hash = selectedParentBlock.postStateHash

    //because we have this block in our local copy of the blockdag
    //and upon including any block in local blockdag we ALWAYS store the post-state of this block in the global states storage
    //so we should be able to retrieve the global state associated with its post-state hash
    val preState: GlobalState[MS] = globalStatesStorage.read(preStateHash).get

    //it is possible that I am not an active validator in this line of the "world evolution"; in such case I of course cannot propose blocks
    //so the only reasonable move is to give up with proposing this block
    if (preState.validatorsBook.currentStakeOf(validatorId) == 0)
      return

    //we clear the buffer of transactions, taking all of them to be included in the new block
    val transactions = deployBuffer.toIndexedSeq
    deployBuffer.clear()

    //now we physically make the new block
    val pTimeOfTheNewBlock: Gas = selectedParentBlock.pTime + selectedParentBlock.gasBurned
    val parents: IndexedSeq[Block] = IndexedSeq(selectedParentBlock)
    val justifications: IndexedSeq[Block] = if (latestMessages.isEmpty) IndexedSeq(genesisBlock) else latestMessages.values.toIndexedSeq
    val dagLevel = parents.map(_.dagLevel).max + 1
    val myLatestBlockPositionInMyChain: Int = myOwnLatestBlock match {
      case None => 0
      case Some(b) => b.positionInPerValidatorChain
    }
    val blockCreationResult: BlocksExecutor.BlockCreationResult[MS] = blocksExecutor.createBlock(
      preState,
      pTimeOfTheNewBlock,
      dagLevel,
      myLatestBlockPositionInMyChain + 1,
      validatorId,
      parents,
      justifications,
      transactions)

    //transactions with fatal errors are not included in the new block; before throwing them away we do some logging
    val discardedTransactions: Map[Transaction, TransactionExecutionResult] = blockCreationResult.txExecutionResults filter {case (tx, txStatus) => txStatus.isFatal}
    if (log.isDebugEnabled) {
      for ((tx, txStatus) <- discardedTransactions)
        log.debug(s"${context.timeOfCurrentEvent}: discarding transaction ${tx.id} - fatal error during execution against global state $preStateHash, error = $txStatus")
    }
    stats.deploysDiscarded(discardedTransactions.keys)

    //if the resulting block contains no transactions (= all transactions were discarded due to fatal errors), we discard the whole block
    if (blockCreationResult.block.transactions.isEmpty) {
      log.debug(s"discarding creation of empty block")
      return
    }

    //adding the just created block to the local blockdag
    pDag.insert(blockCreationResult.block, parents) match {
      case DoublyLinkedDag.InsertResult.AlreadyInserted() =>
        //not possible
        throw new RuntimeException("line unreachable")
      case DoublyLinkedDag.InsertResult.MissingTargets(_) =>
        //not possible
        throw new RuntimeException("line unreachable")
      case InsertResult.Success(continuation) =>
        continuation() //actually adding the block happens here
    }

    jDag.insert(blockCreationResult.block, justifications) match {
      case DoublyLinkedDag.InsertResult.AlreadyInserted() =>
        //not possible
        throw new RuntimeException("line unreachable")
      case DoublyLinkedDag.InsertResult.MissingTargets(_) =>
        //not possible
        throw new RuntimeException("line unreachable")
      case InsertResult.Success(continuation) =>
        continuation() //actually adding the block happens here
    }

    //adding the post-state of newly created block to the local storage of global states
    globalStatesStorage.store(blockCreationResult.postState)

    //announcing the new block to all other validators
    gossipService.gossip(blockCreationResult.block)
    log.debug(s"${context.timeOfCurrentEvent}: publishing new block ${blockCreationResult.block}, while my local pDAG is: \n${BlockdagPrettyPrinter.print(pDag)}")

    //storing local pointer to my latest block
    myOwnLatestBlock = Some(blockCreationResult.block)

    //updating stats
    stats.blockWasPublished(blockCreationResult.block)
  }

}

object Validator {
  case object ProposeTik

  sealed trait AddBlockResult
  object AddBlockResult {
    case object AlreadyAdded extends AddBlockResult
    case class MissingJustifications(blocks: IndexedSeq[Block]) extends AddBlockResult
    case object Invalid extends AddBlockResult // TODO: add reason?
    case object Valid extends AddBlockResult
  }
}
