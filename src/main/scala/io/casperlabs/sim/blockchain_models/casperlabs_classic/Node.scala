package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.{Gas, Transaction}
import io.casperlabs.sim.blockchain_components.hashing.FakeHashGenerator
import io.casperlabs.sim.blockchain_components.{Discovery, DoublyLinkedDag, Gossip}
import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult
import io.casperlabs.sim.simulation_framework._

import scala.collection.mutable
import scala.util.Random

class Node(
  override val id: AgentId,
  stakes: Map[AgentId, Int], // TODO: have this information in block instead
  d: Discovery[AgentId, AgentId],
  g: Gossip[AgentId, AgentId, Node.Comm],
  genesis: Block,
  proposeStrategy: Node.ProposeStrategy,
  random: Random
) extends Agent[Node.Comm, Node.Operation, Node.Propose.type] {
  private val deployBuffer: mutable.HashSet[Node.Operation.Deploy] = mutable.HashSet.empty
  private val blockBuffer: mutable.HashSet[Block] = mutable.HashSet.empty
  // TODO: share DAG structure among nodes
  private val pDag: DoublyLinkedDag[Block] = DoublyLinkedDag.pBlockDag(genesis)
  private val jDag: DoublyLinkedDag[Block] = DoublyLinkedDag.jBlockDag(genesis)
  // TODO: do this without a var
  private var lastProposeTime: Timepoint = Timepoint(0L)
  private val fakeHashGenerator = new FakeHashGenerator(random)

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
    val gasBurned: Gas = 1 //todo: actual value of gas burned in this block should be provided here
    val txns = deployBuffer.toIndexedSeq.map(_.t)
    deployBuffer.clear()
    val block = NormalBlock(
      fakeHashGenerator.nextHash(),
      id,
      parents.map(_.dagLevel).max + 1,
      parents,
      latestMessages.values.toIndexedSeq,
      txns,
      pTimeOfThisBlock,
      gasBurned
    )
    handleBlock(block)
  }

  override def startup(): Unit = ???

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
