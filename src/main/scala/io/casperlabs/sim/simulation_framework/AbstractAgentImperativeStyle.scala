package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.Agent.OutgoingMsgEnvelope

import scala.collection.mutable.ArrayBuffer

/**
  * Abstract base class for agent implementations that follow imperative style.
  * We introduce some syntax sugaring around to make messages handling easier and more readable.
  */
abstract class AbstractAgentImperativeStyle[Msg](val ref: AgentId, val label: String) extends Agent[Msg] {
  private var currentTime: Timepoint = Timepoint(0)
  private var currentSender: AgentId = ref
  private var outgoingMessagesContainer: ArrayBuffer[OutgoingMsgEnvelope[Msg]] = ArrayBuffer.empty

  override final def onStartup(time: Timepoint): Agent.MsgHandlingResult[Msg] = ???

  override final def handleMsg(msg: SimEventsQueueItem.AgentToAgentMsg[Msg]): Agent.MsgHandlingResult[Msg] = ???

  override final def handleExternalEvent(event: SimEventsQueueItem.ExternalEvent[Msg]): Agent.MsgHandlingResult[Msg] = ???

  override final def handlePrivateEvent(event: SimEventsQueueItem.PrivateEvent[Msg]): Agent.MsgHandlingResult[Msg] = ???

  def time: Timepoint = {

  }

  def advanceClock(d: TimeDelta): Unit = {


  }

  def startup(): Unit = {

  }

  def onExternalMsg(msg: Msg): Unit = {

  }

  def onTimer(): Unit = {

  }

  def receive(msg: Msg): Unit = {

  }

}
