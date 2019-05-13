package io.casperlabs.sim.simulation_framework

trait PluginContext {
  def selfRef: AgentRef
  def selfLabel: String
  def timeOfCurrentEvent: Timepoint
  def virtualTime: Timepoint
  def advanceCurrentMessageProcessingStopwatch(d: TimeDelta): Unit
  def sendMsg(destination: AgentRef, msg: Any)
  def setTimerEvent(delay: TimeDelta, msg: Any)
  def messageSendingSupport: MessageSendingSupport
}
