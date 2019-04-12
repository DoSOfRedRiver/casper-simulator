package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.{Discovery, Gossip}
import io.casperlabs.sim.blockchain_components.execution_engine.{Account, Transaction}
import io.casperlabs.sim.simulation_framework.{Agent, AgentId, SimEventsQueueItem}

import scala.collection.mutable

class Node(
  override val id: AgentId, 
  account: Account,
  d: Discovery[AgentId, AgentId],
  g: Gossip[AgentId, AgentId, Node.Comm]
) extends Agent[Node.Comm, Node.Operation] {
  private val deployBuffer: mutable.HashSet[Node.Operation.Deploy] = mutable.HashSet.empty
  private val addedBlocks: mutable.HashSet[Block] = mutable.HashSet.empty
  private val blockBuffer: mutable.HashSet[Block] = mutable.HashSet.empty

  override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Node.Comm, Node.Operation]): Agent.MsgHandlingResult[Node.Comm] = msg.payload match {
    case n @ Node.Comm.NewBlock(b) => 
      addBlock(b) match {
        case Node.AddBlockResult.AlreadyAdded => 
          // We got this block already, nothing to do
          Agent.MsgHandlingResult(Nil, 0L)

        case Node.AddBlockResult.MissingJustifications(_) =>
          // We can't add this block yet, nothing to send out at this time
          Agent.MsgHandlingResult(Nil, 0L)

        case Node.AddBlockResult.Invalid => 
          // Something is wrong with the block.
          // No new messages need to be sent. 
          // TODO: slashing
          Agent.MsgHandlingResult(Nil, 0L)

        case Node.AddBlockResult.Valid =>
          // Block is valid, gossip to others.
          g.gossip(n)
          // TODO: Should this somehow expend time?
          Agent.MsgHandlingResult(Nil, 0L)
      }
  }

  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Node.Comm, Node.Operation]): Agent.MsgHandlingResult[Node.Comm] = event.payload match {
    case d: Node.Operation.Deploy => 
      deployBuffer += d // Add to deploy buffer
      Agent.MsgHandlingResult(Nil, 0L) // No new messages need to be sent

    case Node.Operation.Propose =>
      ??? // Propose a new block
  }

  override def startup(): Unit = ???

  def addBlock(b: Block): Node.AddBlockResult = 
    if (addedBlocks.contains(b)) Node.AddBlockResult.AlreadyAdded
    else {
      // We assume that parents are a sub-set of justifications and 
      // therefore only check the justifications.
      // TODO: Confirm this assumption and call block invalid otherwise
      val missingJustifications = b.justifications.filterNot(addedBlocks.contains)

      if (missingJustifications.isEmpty) {
        // TODO: Other validity checks
        // TODO: handle block buffer?
        addedBlocks += b
        Node.AddBlockResult.Valid
      } else {
        blockBuffer += b
        Node.AddBlockResult.MissingJustifications(missingJustifications)
      }
    }
}

object Node {
  sealed trait Comm
  object Comm {
    case class NewBlock(b: Block) extends Comm
  }

  sealed trait Operation
  object Operation {
    case class Deploy(t: Transaction) extends Operation
    case object Propose extends Operation
  }

  sealed trait AddBlockResult
  object AddBlockResult {
    case object AlreadyAdded extends AddBlockResult
    case class MissingJustifications(blocks: IndexedSeq[Block]) extends AddBlockResult
    case object Invalid extends AddBlockResult // TODO: add reason?
    case object Valid extends AddBlockResult
  }
}
