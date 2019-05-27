package io.casperlabs.sim.simulation_framework

import io.casperlabs.sim.simulation_framework.MessageSendingSupport.FutureResponse

/**
  * Message sending syntax sugar.
  */
trait MessageSendingSupport {
  def tell(destination: AgentRef, msg: Any)

  def ask(destination: AgentRef, msg: Any): FutureResponse[Any]
}

object MessageSendingSupport {

  trait FutureResponse[T] {
    def onComplete(handler: Option[T] => Unit): Unit
  }

}
