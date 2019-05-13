package io.casperlabs.sim.simulation_framework

/**
  * Agent's reference. This is like an "address" (pointer) of an agent on the arena of agents.
  * Agents are identified by AgentRef for the purpose of agent-to-agent communication.
  * It means that an agent needs an AgentRef instance to be able to send a message to another agent.
  *
  * We keep the agent references abstract, so that engine implementations can provide convenient
  * implementation (which may be especially important for clustered implementations of the engine.
  */
trait AgentRef {

  /**
    * Message send syntactic sugar.
    * This has the semantics of "tell" (= unidirectional communication, send-and-forget).
    *
    * @param msg
    * @param syntaxMagic
    * @return
    */
  def !!(msg: Any)(implicit syntaxMagic: MessageSendingSupport): Unit = syntaxMagic.tell(this, msg)


  def ??[Msg](msg: Any)(implicit syntaxMagic: MessageSendingSupport): MessageSendingSupport.FutureResponse[Any] = syntaxMagic.ask(this, msg)

}
