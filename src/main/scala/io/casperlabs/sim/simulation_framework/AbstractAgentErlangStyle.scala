package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.OutgoingMsgEnvelope

/**
  * Abstract base class for agent implementations that follow Erlang programming style.
  * This is a mixture of purely functional "actor monad" semantics (states are immutable values, and next state is provided
  * as a result from the handler) but with usual syntax-sugar for sending messages (which is imperative by nature).
  * We also provide imperative handling of simulation time (which allows for more clean msg sending syntax).
  *
  * todo: CAUTION - this class is experimental
  */
abstract class AbstractAgentErlangStyle[State](val ref: AgentRef, val label: String, val initialState: State) extends Agent {
  private var currentState: State = initialState
  private var outgoingMessagesContainer: List[OutgoingMsgEnvelope] = List.empty
  private var currentResponseDelay: TimeDelta = 0L

  protected implicit val syntaxMagic: MessageSendingSupport = new MessageSendingSupport {

    override def tell(destination: AgentRef, msg: Any): Unit = {
      outgoingMessagesContainer = OutgoingMsgEnvelope.Tell(destination, currentResponseDelay, msg) :: outgoingMessagesContainer
    }

    override def ask(destination: AgentRef, msg: Any): MessageSendingSupport.FutureResponse[Any] = ??? //todo: implement support for request-response calls
  }

  /**
    * Called by the engine only once - when this agent starts his life.
    */
  override def onStartup(time: Timepoint): Agent.MsgHandlingResult = {
    outgoingMessagesContainer = List.empty
    currentResponseDelay = 0L
    currentState = this.startup(time)
    Agent.MsgHandlingResult(outgoingMessagesContainer)
  }

  /**
    * Handler of incoming agent-to-agent messages.
    */
  override def handleMessage(msg: SimEventsQueueItem.AgentToAgentMsg): Agent.MsgHandlingResult = {
    outgoingMessagesContainer = List.empty
    currentResponseDelay = 0L
    currentState = this.receive(msg.scheduledDeliveryTime, msg.source, msg.payload)
    Agent.MsgHandlingResult(outgoingMessagesContainer)
  }

  /**
    * Handler of incoming external events.
    */
  override def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent): Agent.MsgHandlingResult = {
    outgoingMessagesContainer = List.empty
    currentResponseDelay = 0L
    currentState = this.onExternalEvent(event.scheduledDeliveryTime, event.payload)
    Agent.MsgHandlingResult(outgoingMessagesContainer)
  }

  /**
    * Handler of incoming private events (= alerts I set for myself)
    */
  override def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent): Agent.MsgHandlingResult = {
    outgoingMessagesContainer = List.empty
    currentResponseDelay = 0L
    currentState = this.onTimer(event.scheduledDeliveryTime, event.payload)
    Agent.MsgHandlingResult(outgoingMessagesContainer)
  }

  /**
    * Handler of "becoming alive" event for this agent.
    * Called only once in the lifetime of the agent.
    */
  protected def startup(time: Timepoint): State

  /**
    * Handler of external event.
    */
  protected def onExternalEvent(time: Timepoint, msg: Any): State

  /**
    * Handler of timer event (= private message).
    */
  protected def onTimer(time: Timepoint, msg: Any): State

  /**
    * Handler of incoming agent-to-agent message.
    */
  protected def receive(time: Timepoint, sender: AgentRef, msg: Any): State

}
