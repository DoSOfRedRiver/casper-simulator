package io.casperlabs.sim.sim_engine_akka


sealed abstract class EngineMsg {
}

//object EngineMsg {
//  case class ExternalEventDelivery(msgQueueRef: Long, externalEvent: ExternalEvent) extends EngineMsg
//  case class AgentToAgentMsgDelivery(msgQueueRef: Long, agentMessage: AgentMsgPayload) extends EngineMsg
//  case class MsgHandlingCompleted(msgQueueRef: Long, outgoingUnicastMessages: Map[AgentMsgPayload, AgentId], outgoingBroadcastMessages: Iterable[AgentMsgPayload]) extends EngineMsg
//  case class Agent(agent: Agent)
//  case class HaltSimulation(errorInfo: String)
//
//
//}
