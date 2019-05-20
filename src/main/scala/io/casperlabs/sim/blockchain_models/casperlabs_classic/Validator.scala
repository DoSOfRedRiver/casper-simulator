package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, BlocksExecutor, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.{DefaultExecutionEngine, Gas, GlobalState, Transaction}
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.blockchain_components.graphs.{DoublyLinkedDag, IndexedTwoArgRelation}
import io.casperlabs.sim.blockchain_components.hashing.Hash
import io.casperlabs.sim.blockchain_components.pretty_printing.{BlockdagPrettyPrinter, BlocksBufferPrettyPrinter}
import io.casperlabs.sim.blockchain_models.casperlabs_classic.Validator.{AddBlockResult, ProposeTik}
import io.casperlabs.sim.simulation_framework._
import org.slf4j.LoggerFactory

import scala.collection.mutable

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
  private val deployBuffer: mutable.HashSet[Transaction] = mutable.HashSet.empty

  //buffer of blocks we failed to integrate with local blockdag because of missing justifications
  //this is represented as 2-arg relation; entry (a,b) in this relation means "block a is missing a justification b"
  private val blockBuffer: IndexedTwoArgRelation[NormalBlock,NormalBlock] = new IndexedTwoArgRelation[NormalBlock,NormalBlock]

  // TODO: share DAG structure among nodes
  //parent-child dag
  private val pDag: DoublyLinkedDag[Block] = DoublyLinkedDag.pBlockDag(genesisBlock)

  //justifications DAG
  private val jDag: DoublyLinkedDag[Block] = DoublyLinkedDag.jBlockDag(genesisBlock)

  //latest block I created and published (if any)
  private val myOwnLatestBlock: Option[NormalBlock] = None

  // TODO: do this without a var
  private var lastProposeTime: Timepoint = Timepoint(0L)

  //we use BinaryArraySpace as our computing space //todo: make this configurable ?
  private val computingSpace: CS = BinaryArraySpace.ComputingSpace

  //execution engine in use //todo: make this configurable ?
  private val executionEngine = new DefaultExecutionEngine(blockchainConfig, computingSpace)

  //blocks executor in use //todo: make this configurable ?
  private val blocksExecutor = new CasperMainchainBlocksExecutor[CS, P, MS](executionEngine, blockchainConfig)

//###################################### PLUGIN CALLBACKS ########################################

  override def startup(): Unit = {
    log.debug(s"${context.timeOfCurrentEvent}: startup of ${context.selfLabel}")
    globalStatesStorage.store(genesisGlobalState)
    thisAgent.setTimerEvent(proposeDelay, ProposeTik)
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
    log.trace(s"${context.timeOfCurrentEvent}: received block ${block.id} with daglevel=${block.dagLevel} created by ${block.creator}, current blocks buffer is: ${BlocksBufferPrettyPrinter.print(blockBuffer)}")

    validateIncomingBlockAndAttemptAddingItToLocalBlockdag(block) match {
      case AddBlockResult.AlreadyAdded =>
        // We got this block already, nothing to do
        log.trace("incoming block case: already added")

      case AddBlockResult.MissingJustifications(justifications) =>
        for (j <- justifications)
          blockBuffer.addPair(block, j.asInstanceOf[NormalBlock]) //this casting is legal because Genesis block is always present (hence cannot be missing and registered as missing dependency)
        //todo: add block buffer purging here (if a block stays in the buffer for too long, it should be discarded because we assume it must be a side-effect of hacking attempt)
        log.trace("incoming block case: missing justification")

      case AddBlockResult.Invalid =>
        // Something is wrong with the block.
        // No new messages need to be sent.
        // TODO: slashing
        log.trace("incoming block case: invalid")

      case AddBlockResult.Valid =>
        // Block is valid, gossip to others
        //todo: this does not work correctly with current mock of gossiping (clarify/fix this !)
        //gossipService.gossip(Node.Comm.NewBlock(b))
        log.trace("incoming block case: valid")

        //possibly this new block unlocked some other blocks in the blocks buffer
        val blocksWaitingForThisOne = blockBuffer.findSourcesFor(block)
        blockBuffer.removeTarget(block)
        if (blocksWaitingForThisOne.nonEmpty) {
          for (a <- blocksWaitingForThisOne)
            if (!blockBuffer.hasSource(a))
              this.validateIncomingBlockAndAttemptAddingItToLocalBlockdag(a) //we are not checking the result because this time is must be a success //todo: add assertion here ?
        }

        log.trace(s"final pDag: \n ${BlockdagPrettyPrinter.print(pDag)}")
        log.trace(s"final blocks buffer: \n: ${BlocksBufferPrettyPrinter.print(blockBuffer)}")
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
      log.debug(s"block ${block.id}: pre-state hash mismatch")
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
          log.debug(s"block ${block.id}: post-state mismatch")
          return false
        }
      case None =>
        //if this happens then we have a bug; successful insertion in pDAG implies that we (should) have seen and validated parents before
        throw new RuntimeException(s"pre-state of block ${block.id} was not found in storage")
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
    val justifications: IndexedSeq[Block] = latestMessages.values.toIndexedSeq
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
    if (log.isDebugEnabled) {
      for ((txHash, txStatus) <- blockCreationResult.txExecutionResults if txStatus.isFatal)
        log.debug(s"${context.timeOfCurrentEvent}: discarding transaction $txHash - fatal error during execution against global state $preStateHash, error = $txStatus")
    }

    //if the resulting block contains no transactions (= all transactions were discarded due to fatal errors), we discard the whole block
    if (blockCreationResult.block.transactions.isEmpty) {
      log.debug(s"discarding creation of empty block")
      return
    }

    //adding the just created block to the local blockdag
    pDag.insert(blockCreationResult.block, parents)
    jDag.insert(blockCreationResult.block, justifications)

    //adding the post-state of newly created block to the local storage of global states
    globalStatesStorage.store(blockCreationResult.postState)

    //announcing the new block to all other validators
    gossipService.gossip(blockCreationResult.block)
    log.debug(s"${context.timeOfCurrentEvent}: publishing new block ${blockCreationResult.block.id} with daglevel=$dagLevel (${blockCreationResult.block.transactions.size} transactions)")
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
