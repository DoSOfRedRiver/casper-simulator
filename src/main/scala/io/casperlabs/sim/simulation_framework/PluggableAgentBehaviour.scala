package io.casperlabs.sim.simulation_framework

/**
  * Contract for components that can be plugged into agents, following the stackable event handlers model.
  * See class AbstractAgentWithPluggableBehaviours.
  */
trait PluggableAgentBehaviour {
  private var context: PluginContext = _
  protected implicit lazy val syntaxMagic: MessageSendingSupport = context.messageSendingSupport

  def initContext(c: PluginContext): Unit = {
    context = c
  }

  /**
    * Syntax sugar for using partial functions as handlers.
    * When partial function matches an argument, we automatically signal consumption of the message.
    * Otherwise the message is left unconsumed.
    *
    * Usage example:
    * code below:
    * <pre>
    *   consumeIfMatched(msg) {
    *     case Foo(x,y) => doSomethingNice(x+y)
    *     case Bar => doSomethingElse(42)
    *     case Baz if currentTime > criticalValue => router !! Messages.Restart
    *   }
    * </pre>
    *
    * ... is more readable version of this one:
    *
    * <pre>
    *   msg match {
    *     case Foo(x,y) =>
    *       doSomethingNice(x+y)
    *       true
    *     case Bar =>
    *       doSomethingElse(42)
    *       true
    *     case Baz if currentTime > criticalValue =>
    *       router !! Messages.Restart
    *       true
    *       case _ =>
    *       false
    *   }
    * </pre>
    */
  protected def consumeIfMatched(msg: Any)(cases: PartialFunction[Any, Unit]): Boolean =
    cases.lift.apply(msg) match {
      case Some(_) => true
      case None => false
    }

  /**
    * Access to features of the owning agent (= where this plugin is plugged to).
    */
  protected def thisAgent: PluginContext = context

  protected def findAgent(label: String): Option[AgentRef] = context.findAgent(label)

  def startup(): Unit

  def onExternalEvent(msg: Any): Boolean

  def onTimer(msg: Any): Boolean

  def receive(sender: AgentRef, msg: Any): Boolean

}
