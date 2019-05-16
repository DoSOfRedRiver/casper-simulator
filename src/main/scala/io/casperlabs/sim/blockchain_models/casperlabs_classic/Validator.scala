package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.{DefaultExecutionEngine, Gas, GlobalState, Transaction}
import io.casperlabs.sim.blockchain_components.gossip.Gossip
import io.casperlabs.sim.blockchain_components.graphs.{DoublyLinkedDag, IndexedTwoArgRelation}
import io.casperlabs.sim.blockchain_components.hashing.Hash
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

  // TODO: do this without a var
  private var lastProposeTime: Timepoint = Timepoint(0L)

  //we use BinaryArraySpace as our computing space //todo: make this configurable ?
  private val computingSpace: CS = BinaryArraySpace.ComputingSpace

  //initial memory state (belongs to computing space)
  private val initialMemoryState: MS = computingSpace.initialState

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
    log.debug(s"${context.timeOfCurrentEvent}: received block ${block.id} with daglevel=${block.dagLevel} created by ${block.creator}")

    attemptAddingIncomingBlockToLocalBlockdag(block) match {
      case AddBlockResult.AlreadyAdded =>
      // We got this block already, nothing to do

      case AddBlockResult.MissingJustifications(justifications) =>
        for (j <- justifications)
          blockBuffer.addPair(block, j.asInstanceOf[NormalBlock]) //this casting is legal because Genesis block is always present (hence cannot be missing and registered as missing dependency)
      //todo: add block buffer purging here (if a block stays in the buffer for too long, it should be discarded because we assume it must be a side-effect of hacking attempt)

      case AddBlockResult.Invalid =>
      // Something is wrong with the block.
      // No new messages need to be sent.
      // TODO: slashing

      case AddBlockResult.InvalidPostStateHash =>
      //todo: slashing

      case AddBlockResult.Valid =>
        // Block is valid, gossip to others
        //todo: this does not work correctly with current mock of gossiping (clarify/fix this !)
        //gossipService.gossip(Node.Comm.NewBlock(b))

        //possibly this new block unlocked some other blocks in the blocks buffer
        val blocksWaitingForThisOne = blockBuffer.findSourcesFor(block)
        blockBuffer.removeTarget(block)
        if (blocksWaitingForThisOne.nonEmpty) {
          for (a <- blocksWaitingForThisOne)
            if (!blockBuffer.hasSource(a))
              this.attemptAddingIncomingBlockToLocalBlockdag(a) //we are not checking the result because this time is must be a success //todo: add assertion here ?
        }
    }

  }

  private def attemptAddingIncomingBlockToLocalBlockdag(b: NormalBlock): AddBlockResult =
    (pDag.insert(b, b.parents), jDag.insert(b, b.justifications)) match {
      case (DoublyLinkedDag.InsertResult.Success(pInsert), DoublyLinkedDag.InsertResult.Success(jInsert)) =>
        if (verifyPostStateHash(b)) {
          pInsert()
          jInsert()
          AddBlockResult.Valid
        } else {
          AddBlockResult.InvalidPostStateHash
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

  private def verifyPostStateHash(block: NormalBlock): Boolean = {
    globalStatesStorage.read(block.preStateHash) match {
      case Some(preState) =>
        val (gs, gas) = blocksExecutor.executeBlockAsVerifier(preState, block)
        val postStateHash = executionEngine.globalStateHash(gs)
        if (postStateHash == block.postStateHash && gas == block.gasBurned) {
          globalStatesStorage.store(gs)
          return true
        } else {
          //this block is invalid, we just throw it away
          //todo: logging here
          return false
        }
      case None =>
        throw new RuntimeException(s"pre-state of block ${block.id} was not found in storage")
    }
  }

  private def decideIfWeWantToProposeBlockNowAndCalculateDelayToNextWakeup(): (Boolean, TimeDelta) = {
    //todo: replace this method with proper abstraction of blocks proposing strategy (should this be another PluggableAgentBehaviour ?)
    val diff = thisAgent.timeOfCurrentEvent - lastProposeTime
    (diff >= proposeDelay, proposeDelay)
  }

  private[this] def createAndPublishNewBlock(): Unit = {
    // TODO: check for equivocations?
    val latestMessages: Map[ValidatorId, Block] = (jDag.tips.groupBy(_.creator) - Block.psuedoValidatorIdUsedForGenesisBlock).mapValues(_.head)

    val selectedParentBlock: Block = BlockdagUtils.lmdMainchainGhost[ValidatorId, Block, Hash](
      latestMessages,
      validatorWeightExtractor = (block, vid) => block.weightsMap.getOrElse(vid, 0L),
      pDag,
      genesisBlock,
      tieBreaker = block => block.id
    )

    val parents: IndexedSeq[Block] = IndexedSeq(selectedParentBlock)

    //todo: implement merging or parents; for now we implement block-tree only

    val pTimeOfThisBlock: Gas = selectedParentBlock.pTime + selectedParentBlock.gasBurned
    val txns = deployBuffer.toIndexedSeq
    deployBuffer.clear()
    val preStateHash: Hash = selectedParentBlock.postStateHash
    val preState: GlobalState[MS] = globalStatesStorage.read(preStateHash).get
    val justifications: IndexedSeq[Block] = latestMessages.values.toIndexedSeq
    val justificationsIds: IndexedSeq[Hash] = justifications.map(b => b.id)

    val newBlockId: Hash = blocksExecutor.calculateBlockId(
      validatorId,
      parents = IndexedSeq(selectedParentBlock.id),
      justificationsIds,
      txns,
      preStateHash
    )

    val (postState, gasBurned) = blocksExecutor.executeBlockAsCreator(preState, pTimeOfThisBlock, newBlockId, validatorId, txns)
    val postStateHash = executionEngine.globalStateHash(postState)

    val block = NormalBlock(
      id = newBlockId,
      creator = validatorId,
      dagLevel = parents.map(_.dagLevel).max + 1,
      parents,
      justifications,
      txns,
      pTimeOfThisBlock,
      gasBurned,
      postState.validatorsBook.validatorWeightsMap,
      preStateHash,
      postStateHash
    )

    pDag.insert(block, parents)
    jDag.insert(block, block.justifications)
    globalStatesStorage.store(postState)

    gossipService.gossip(block)

    log.debug(s"${context.timeOfCurrentEvent}: publishing new block ${block.id} with daglevel=${block.dagLevel} and ${block.transactions.size} transactions")
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
    case object InvalidPostStateHash extends AddBlockResult
  }
}
