package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.OutgoingMsgEnvelope

/**
  * Abstract base class for agents that follow "akka" style.
  * We provide explicit stackable/pluggable in-agent components mechanics.
  */
abstract class AbstractAgentWithPluggableBehaviours[R](val ref: AgentRef, val label: String, plugins: List[PluggableAgentBehaviour]) extends Agent[R] {
  private var arrivalTimeOfCurrentEvent: Timepoint = Timepoint(0)
  private var currentMsgProcessingClock: TimeDelta = 0L
  private var currentSender: AgentRef = ref
  private var outgoingMessagesContainer: List[OutgoingMsgEnvelope] = List.empty

  protected implicit val syntaxMagic: MessageSendingSupport = new MessageSendingSupport {

    override def tell(destination: AgentRef, msg: Any): Unit = pluginContext.sendMsg(destination, msg)

    override def ask(destination: AgentRef, msg: Any): MessageSendingSupport.FutureResponse[Any] = ??? //todo: implement support for request-response calls
  }

  private val pluginContext = new PluginContext {
    override def selfRef: AgentRef = ref

    override def selfLabel: String = label

    override def timeOfCurrentEvent: Timepoint = AbstractAgentWithPluggableBehaviours.this.timeOfCurrentEvent

    override def virtualTime: Timepoint = AbstractAgentWithPluggableBehaviours.this.virtualTime

    override def advanceCurrentMessageProcessingStopwatch(d: TimeDelta): Unit = AbstractAgentWithPluggableBehaviours.this.advanceCurrentMessageProcessingStopwatch(d)

    override def sendMsg(destination: AgentRef, msg: Any): Unit = {
      OutgoingMsgEnvelope.Tell(destination, currentMsgProcessingClock, msg) :: outgoingMessagesContainer
    }

    override def setTimerEvent(delay: TimeDelta, msg: Any): Unit = {
      OutgoingMsgEnvelope.Private(currentMsgProcessingClock, delay, msg) :: outgoingMessagesContainer
    }

    override def messageSendingSupport: MessageSendingSupport = syntaxMagic
  }

  //initialize plugins
  for (p <- plugins)
    p.initContext(pluginContext)

  override final def onStartup(time: Timepoint): Agent.MsgHandlingResult[R] = {
    arrivalTimeOfCurrentEvent = time
    for (p <- plugins)
      p.startup()
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil) //todo: provide support for recording, i.e. replace Nil with actual items to be recorded
  }

  override final def handleMessage(msg: SimEventsQueueItem.AgentToAgentMsg): Agent.MsgHandlingResult[R] =
    handleEvent(msg.scheduledDeliveryTime, msg.source){
      p => p.receive(msg.source, msg.payload)
    }

  override final def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent): Agent.MsgHandlingResult[R] =
    handleEvent(event.scheduledDeliveryTime, ref){
      p => p.onExternalEvent(event.payload)
    }

  override final def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent): Agent.MsgHandlingResult[R] =
    handleEvent(event.scheduledDeliveryTime, ref){
      p => p.onTimer(event.payload)
    }

  protected def timeOfCurrentEvent: Timepoint = arrivalTimeOfCurrentEvent

  protected def virtualTime: Timepoint = arrivalTimeOfCurrentEvent + currentMsgProcessingClock

  private def handleEvent(deliveryTime: Timepoint, senderToBeUsed: AgentRef)(action: PluggableAgentBehaviour => Boolean): Agent.MsgHandlingResult[R] = {
    outgoingMessagesContainer = List.empty
    arrivalTimeOfCurrentEvent = deliveryTime
    currentMsgProcessingClock = 0L
    currentSender = senderToBeUsed
    handleWithPluginsStack(action)
    return Agent.MsgHandlingResult(outgoingMessagesContainer, Nil) //todo: provide support for recording, i.e. replace Nil with actual items to be recorded
  }

  private def handleWithPluginsStack(action: PluggableAgentBehaviour => Boolean): Unit = {
    for (p <- plugins) {
      val consumed = action(p)
      if (consumed)
        return
    }
  }

  /**
    * Registers processing time passed while handling the current event.
    */
  protected def advanceCurrentMessageProcessingStopwatch(d: TimeDelta): Unit = {
    currentMsgProcessingClock += d
  }

}
