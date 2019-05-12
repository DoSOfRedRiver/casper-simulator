package io.casperlabs.sim.simulation_framework

/**
  * Abstract base class for agent implementations that follow pure FP style.
  * We introduce some syntax sugaring around to make messages handling easier and more readable.
  */
class AbstractAgentFPStyle[Msg](ref: AgentId, label: String) extends Agent[Msg] {

  /**
    * My reference (given by the engine).
    */
  override def ref: AgentId = ???

  /**
    * User-readable identifier of an agent - something like "client-324" or "server-15" or "bridge-1".
    * In any user-friendly presentation of the arena, names are going to be the primary way of "labelling" agents.
    * This name must be unique on the arena and must never change !
    * Agents naming schema is not decided here - we expect this to be heavily model-specific.
    */
  override def label: String = ???

  /**
    * Called by the engine only once - when this agent starts his life.
    */
  override def onStartup(time: Timepoint): Agent.MsgHandlingResult[Msg] = ???

  /**
    * Handler of incoming agent-to-agent messages.
    */
  override def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Msg]): Agent.MsgHandlingResult[Msg] = ???

  /**
    * Handler of incoming external events.
    */
  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Msg]): Agent.MsgHandlingResult[Msg] = ???

  /**
    * Handler of incoming private events (= alerts I set for myself)
    */
  override def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[Msg]): Agent.MsgHandlingResult[Msg] = ???
}
