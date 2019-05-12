package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.abstract_blockchain.BlockchainConfig
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.{DefaultExecutionEngine, Gas, GlobalState, Transaction}
import io.casperlabs.sim.blockchain_components.{Discovery, DoublyLinkedDag, Gossip}
import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult
import io.casperlabs.sim.simulation_framework._

import scala.collection.mutable

class Node(
            override val ref: AgentId,
            stakes: Map[AgentId, Int], // TODO: have this information in block instead
            d: Discovery[AgentId, AgentId],
            g: Gossip[AgentId, AgentId, Node.Comm],
            genesis: Block,
            proposeStrategy: Node.ProposeStrategy,
            config: BlockchainConfig
) extends Agent[Node.Comm, Node.Operation, Node.Propose.type] {

  type MS = BinaryArraySpace.MemoryState
  type P = BinaryArraySpace.Program
  type CS = ComputingSpace[P,MS]
  type GS = GlobalState[MS]

  private val deployBuffer: mutable.HashSet[Node.Operation.Deploy] = mutable.HashSet.empty
  private val blockBuffer: mutable.HashSet[Block] = mutable.HashSet.empty
  // TODO: share DAG structure among nodes
  private val pDag: DoublyLinkedDag[Block] = DoublyLinkedDag.pBlockDag(genesis)
  private val jDag: DoublyLinkedDag[Block] = DoublyLinkedDag.jBlockDag(genesis)
  // TODO: do this without a var
  private var lastProposeTime: Timepoint = Timepoint(0L)

  val computingSpace: CS = BinaryArraySpace.ComputingSpace
  val initialMemoryState: MS = computingSpace.initialState
  val ee = new DefaultExecutionEngine(config, computingSpace)
  val blocksExecutor = new CasperMainchainBlocksExecutor[CS,P,MS](ee, config)

  def getLastProposedTime: Timepoint = lastProposeTime

  override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Node.Comm, Node.Operation, Node.Propose.type]): Agent.MsgHandlingResult[Node.Comm, Node.Propose.type] = msg.payload match {
    case Node.Comm.NewBlock(b) =>
      handleBlock(b)
  }

  def handleBlock(b: Block): MsgHandlingResult[Node.Comm, Node.Propose.type] =
    addBlock(b) match {
      case Node.AddBlockResult.AlreadyAdded =>
        // We got this block already, nothing to do
        Agent.MsgHandlingResult.empty

      case Node.AddBlockResult.MissingJustifications(_) =>
        // We can't add this block yet, nothing to send out at this time
        Agent.MsgHandlingResult.empty

      case Node.AddBlockResult.Invalid =>
        // Something is wrong with the block.
        // No new messages need to be sent.
        // TODO: slashing
        Agent.MsgHandlingResult.empty

      case Node.AddBlockResult.Valid =>
        // Block is valid, gossip to others.
        g.gossip(Node.Comm.NewBlock(b))
        // TODO: Should this somehow expend time?
        Agent.MsgHandlingResult.empty
    }

  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Node.Comm, Node.Operation, Node.Propose.type]): Agent.MsgHandlingResult[Node.Comm, Node.Propose.type] = event.payload match {
    case Node.Operation.NoOp =>
      Agent.MsgHandlingResult.empty // Nothing to do here

    case d: Node.Operation.Deploy =>
      deployBuffer += d // Add to deploy buffer
      Agent.MsgHandlingResult.empty // No new messages need to be sent
  }

  override def handlePrivateEvent(event: Node.ProposeEvent): MsgHandlingResult[Node.Comm, Node.Propose.type] = {
    val (shouldPropose, nextEventTime) = proposeStrategy(this, event)
    val Agent.MsgHandlingResult(out, proposals, time) =
      if (shouldPropose) {
        lastProposeTime = event.scheduledTime
        doPropose
      }
      else MsgHandlingResult.empty

    Agent.MsgHandlingResult(out, proposals ++ List(nextEventTime -> Node.Propose), time)
  }

  private[this] def doPropose: Agent.MsgHandlingResult[Node.Comm, Node.Propose.type] = {
    // Propose a new block
    // TODO: check for equivocations?
    val latestMessages = jDag.tips.groupBy(_.creator).mapValues(_.head)
    val parents = BlockdagUtils.lmdGhost(
      latestMessages,
      stakes,
      pDag,
      genesis
    )
    // TODO: use execution engine to process deploys
    val pTimeOfThisBlock: Gas = 1 //todo: actual value of p-time should be provided here
    val txns = deployBuffer.toIndexedSeq.map(_.t)
    deployBuffer.clear()


    //todo: replace main parent post-state hash with real hash of merged pre-state
    //todo: clean-up separation between agent-id and validator-id

    val parentsIds = parents.map(b => b.id)
    val justifications = latestMessages.values.toIndexedSeq
    val justificationsIds = justifications.map(b => b.id)
    val newBlockId = blocksExecutor.calculateBlockId(ref, parentsIds, justificationsIds, txns, parents(0).postStateHash)

    //todo: we need a real pre-state here
    val preState: GlobalState[MS] = GlobalState.empty(computingSpace)

    val (postState, gasBurned) = blocksExecutor.executeBlockAsCreator(preState, pTimeOfThisBlock, newBlockId, ref, txns)

    val block = NormalBlock(
      id = newBlockId,
      creator = this.ref,
      dagLevel = parents.map(_.dagLevel).max + 1,
      parents,
      justifications,
      txns,
      pTimeOfThisBlock,
      gasBurned,
      preStateHash = ee.globalStateHash(preState),
      postStateHash = ee.globalStateHash(postState)
    )
    handleBlock(block)
  }

  override def onStartup(): Unit = ???

  def addBlock(b: Block): Node.AddBlockResult =
    (pDag.insert(b, b.parents), jDag.insert(b, b.justifications)) match {
      case (
        DoublyLinkedDag.InsertResult.Success(pInsert),
        DoublyLinkedDag.InsertResult.Success(jInsert),
        ) =>
        // TODO: Other validity checks
        // TODO: handle block buffer
        pInsert()
        jInsert()
        Node.AddBlockResult.Valid

      case (_, DoublyLinkedDag.InsertResult.MissingTargets(jMissing)) =>
        // We assume that parents are a sub-set of justifications and
        // therefore only check the justifications.
        // TODO: Confirm this assumption and call block invalid otherwise
        blockBuffer += b
        Node.AddBlockResult.MissingJustifications(jMissing.toIndexedSeq)

      case (_, DoublyLinkedDag.InsertResult.AlreadyInserted()) =>
        Node.AddBlockResult.AlreadyAdded

      case (_, DoublyLinkedDag.InsertResult.Success(_)) =>
        throw new RuntimeException("Unreachable state")
    }
}

object Node {
  type ProposeEvent = SimEventsQueueItem.PrivateEvent[Node.Comm, Node.Operation, Node.Propose.type]
  type ProposeStrategy = (Node, Node.ProposeEvent) => (Boolean, Timepoint)

  def intervalPropose(delay: TimeDelta): ProposeStrategy = (node, event) => {
    val lastProposedTime = node.getLastProposedTime
    val diff = event.scheduledTime - lastProposedTime
    (diff >= delay, event.scheduledTime + delay)
  }

  sealed trait Comm
  object Comm {
    case class NewBlock(b: Block) extends Comm
  }

  sealed trait Operation
  object Operation {
    case class Deploy(t: Transaction) extends Operation
    // "Do nothing command", needed to allow infinite external events
    case object NoOp extends Operation

    def deploy(t: Transaction): Operation = Deploy(t)
    def noOp: Operation = NoOp
  }

  case object Propose

  sealed trait AddBlockResult
  object AddBlockResult {
    case object AlreadyAdded extends AddBlockResult
    case class MissingJustifications(blocks: IndexedSeq[Block]) extends AddBlockResult
    case object Invalid extends AddBlockResult // TODO: add reason?
    case object Valid extends AddBlockResult
  }
}
