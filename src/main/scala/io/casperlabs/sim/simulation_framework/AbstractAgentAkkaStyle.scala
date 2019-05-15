package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.OutgoingMsgEnvelope

/**
  * Abstract base class for agent implementations that follow "Akka" style (= with mutable state, usual syntax
  * for tell/ask message sends and sender/current time information available as part of execution context).
  *
  * todo: CAUTION - this class is experimental
  */
abstract class AbstractAgentAkkaStyle[R](val ref: AgentRef, val label: String) extends Agent[R] {
  private var arrivalTimeOfCurrentEvent: Timepoint = Timepoint(0)
  private var currentResponseDelay: TimeDelta = 0L
  private var currentSender: AgentRef = ref
  private var outgoingMessagesContainer: List[OutgoingMsgEnvelope] = List.empty

  protected implicit val syntaxMagic: MessageSendingSupport = new MessageSendingSupport {

    override def tell(destination: AgentRef, msg: Any): Unit = {
      outgoingMessagesContainer = OutgoingMsgEnvelope.Tell(destination, currentResponseDelay, msg) :: outgoingMessagesContainer
    }

    override def ask(destination: AgentRef, msg: Any): MessageSendingSupport.FutureResponse[Any] = ??? //todo: implement support for request-response calls
  }

  override final def onStartup(time: Timepoint): Agent.MsgHandlingResult[R] = {
    arrivalTimeOfCurrentEvent = time
    this.startup()
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil)
  }

  override final def handleMessage(msg: SimEventsQueueItem.AgentToAgentMsg): Agent.MsgHandlingResult[R] = {
    outgoingMessagesContainer = List.empty
    arrivalTimeOfCurrentEvent = msg.scheduledDeliveryTime
    currentResponseDelay = 0L
    currentSender = msg.source
    this.receive(msg.payload)
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil)
  }

  override final def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent): Agent.MsgHandlingResult[R] = {
    outgoingMessagesContainer = List.empty
    arrivalTimeOfCurrentEvent = event.scheduledDeliveryTime
    currentResponseDelay = 0L
    currentSender = ref
    this.onExternalEvent(event.payload)
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil)
  }

  override final def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent): Agent.MsgHandlingResult[R] = {
    outgoingMessagesContainer = List.empty
    arrivalTimeOfCurrentEvent = event.scheduledDeliveryTime
    currentResponseDelay = 0L
    currentSender = ref
    this.onTimer(event.payload)
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil)
  }

  protected def timeOfCurrentEvent: Timepoint = arrivalTimeOfCurrentEvent

  protected def virtualTime: Timepoint = arrivalTimeOfCurrentEvent + currentResponseDelay

  /**
    * Registers processing time passed while handling the current event.
    */
  protected def advanceCurrentMessageProcessingStopwatch(d: TimeDelta): Unit = {
    currentResponseDelay += d
  }

  /**
    * Handler of "becoming alive" event for this agent.
    * Called only once in the lifetime of the agent.
    */
  protected def startup(): Unit

  /**
    * Handler of external event.
    */
  protected def onExternalEvent(msg: Any): Unit

  /**
    * Handler of timer event (= private message).
    */
  protected def onTimer(msg: Any): Unit

  /**
    * Handler of incoming agent-to-agent message.
    */
  protected def receive(msg: Any): Unit

}
