package io.casperlabs.sim.blockchain_models.casperlabs

import io.casperlabs.sim.simulation_framework.{Agent, AgentId, SimEventsQueueItem, SimulationContext}

class Node(account: Account, context: SimulationContext) extends Agent(context) {
  //block -> collection of missing justifications; once this collection goes down to empty, we are ready to include the block in the node view of the blockdag
  private val receivedBlocksBuffer: BlocksMultimap = ???

  override def id: AgentId = ???

  override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Nothing, Nothing]): Agent.MsgHandlingResult[Nothing] = ???

  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Nothing, Nothing]): Agent.MsgHandlingResult[Nothing] = ???

  override def startup(): Unit = ???

  def currentSharedDag: SharedBlockdag = ???

}
