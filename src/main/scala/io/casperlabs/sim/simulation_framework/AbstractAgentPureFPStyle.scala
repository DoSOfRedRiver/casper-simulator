package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.OutgoingMsgEnvelope

/**
  * Abstract base class for agent implementations that follow "pure" functional programming style.
  * We introduce some syntax sugaring around to make messages handling easier and more readable.
  *
  * This implementation of Agent tries to mimic Erlang-style actors. We use simple means however (as opposed to Actor monad for example).
  * Conceptually this is also similar to Akka FSM actors.
  * And agent has mutable state, but this is the engine (as opposed to the developer) who updates the state.
  * So developer always stays "pure" - only writing pure functions and not using var-s.
  * This is very much like with using the State monad.
  *
  * On receiving an event a proper handler is invoked. The responsibility of the handler is:
  * (a) produce new (immutable) state
  * (b) produce a (possibly empty) collection of messages to be sent to other agents
  *
  * Messages created in (b) include virtual time information, so "how much virtual time was consumed for processing".
  *
  * todo: CAUTION - this class is experimental
  */
abstract class AbstractAgentPureFPStyle[State, R](val label: String, val initialState: State) extends Agent[R] {
  private var privateRef: Option[AgentRef] = None
  private var context: Option[AgentContext] = None
  private var currentState: State = initialState

  override def initRef(r: AgentRef): Unit = {
    privateRef match {
      case Some(_) => throw new RuntimeException("attempted to re-set agent id")
      case None =>
        privateRef = Some(r)
    }
  }

  override def ref: AgentRef = privateRef.get

  override def initContext(c: AgentContext): Unit = {
    context = Some(c)
  }

  /**
    * Called by the engine only once - when this agent starts his life.
    */
  override def onStartup(time: Timepoint): Agent.MsgHandlingResult[R] = {
    val (messages, newState)  = this.startup(time)
    currentState = newState
    Agent.MsgHandlingResult(messages, Nil)
  }

  override def onSimulationEnd(time: Timepoint): Agent.MsgHandlingResult[R] = ??? //todo

  /**
    * Handler of incoming agent-to-agent messages.
    */
  override def handleMessage(msg: SimEventsQueueItem.AgentToAgentMsg): Agent.MsgHandlingResult[R] = {
    val (messages, newState)  = this.receive(msg.scheduledDeliveryTime, msg.source, msg.payload)
    currentState = newState
    Agent.MsgHandlingResult(messages, Nil)
  }

  /**
    * Handler of incoming external events.
    */
  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent): Agent.MsgHandlingResult[R] = {
    val (messages, newState)  = this.onExternalEvent(event.scheduledDeliveryTime, event.payload)
    currentState = newState
    Agent.MsgHandlingResult(messages, Nil)
  }

  /**
    * Handler of incoming private events (= alerts I set for myself)
    */
  override def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent): Agent.MsgHandlingResult[R] = {
    val (messages, newState)  = this.onTimer(event.scheduledDeliveryTime, event.payload)
    currentState = newState
    Agent.MsgHandlingResult(messages, Nil)
  }

  /**
    * Handler of "becoming alive" event for this agent.
    * Called only once in the lifetime of the agent.
    */
  protected def startup(time: Timepoint): (List[OutgoingMsgEnvelope], State)

  /**
    * Handler of external event.
    */
  protected def onExternalEvent(time: Timepoint, msg: Any): (List[OutgoingMsgEnvelope], State)

  /**
    * Handler of timer event (= private message).
    */
  protected def onTimer(time: Timepoint, msg: Any): (List[OutgoingMsgEnvelope], State)

  /**
    * Handler of incoming agent-to-agent message.
    */
  protected def receive(time: Timepoint, sender: AgentRef, msg: Any): (List[OutgoingMsgEnvelope], State)
}
