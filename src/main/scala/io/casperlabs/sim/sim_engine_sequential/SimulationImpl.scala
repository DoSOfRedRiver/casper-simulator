package io.casperlabs.sim.sim_engine_sequential

import io.casperlabs.sim.simulation_framework.Agent.MsgHandlingResult
import io.casperlabs.sim.simulation_framework.SimEventsQueueItem._
import io.casperlabs.sim.simulation_framework._
import org.slf4j.LoggerFactory

import scala.collection.mutable

class SimulationImpl[R](simulationEnd: Timepoint, networkBehavior: NetworkBehavior) extends Simulation[R] {
  private val log = LoggerFactory.getLogger("sim-engine")

  private val queue: SimEventsQueue[R] = new SimEventsQueue
  private val agentsRegistry = new mutable.HashMap[AgentRef, Agent[R]]
  private val label2Agent = new mutable.HashMap[String, Agent[R]]
  private var clock: Timepoint = Timepoint(0L)
  private val agentIdGenerator = Iterator.iterate(0)(_ + 1)
  private val msgIdGenerator = Iterator.iterate(0L)(_ + 1L)

  private val sharedContext = new AgentContext {
    override def findAgent(label: String): Option[AgentRef] = label2Agent.get(label).map(_.ref)
  }

  override def preRegisterAgent(agent: Agent[R]): AgentRef = {
    val agentRef = privateRegisterAgent(agent)
    agent.onStartup(Timepoint.zero)
    return agentRef
  }

  override def currentTime(): Timepoint = clock

  override def start(
                      externalEventsGenerator: ExternalEventsStream,
                      agentsCreationStream: AgentsCreationStream[R]
                    ): Unit = {
    log.debug("starting simulation")

    queue.plugInAgentsCreationStream(agentsCreationStream)
    queue.plugInExternalEventsStream(externalEventsGenerator)

    while (clock <= simulationEnd) {
      queue.dequeue() match {
        case None =>
          //no more events to process, this is the end of simulation
          return
        case Some(event) =>
          clock = event.scheduledDeliveryTime
          event match {
            case msg: AgentToAgentMsg =>
              log.debug(s"$clock: delivering msg ${msg.payload.getClass.getSimpleName} from ${msg.source} to ${msg.destination}")
              val agent = unsafeGetAgent(msg.destination, msg)
              val processingResult: MsgHandlingResult[R] = agent.handleMessage(msg)
              applyEventProcessingResultToSimState(msg.destination, processingResult)

            case ev: ExternalEvent =>
              log.debug(s"$clock: delivering ext event ${ev.payload.getClass.getSimpleName} to ${ev.affectedAgent}")
              val agent = unsafeGetAgent(ev.affectedAgent, ev)
              val processingResult: MsgHandlingResult[R] = agent.handleExternalEvent(ev)
              applyEventProcessingResultToSimState(ev.affectedAgent, processingResult)

            case ev: NewAgentCreation[R] =>
              privateRegisterAgent(ev.agentInstance)
              ev.agentInstance.onStartup(ev.scheduledDeliveryTime)

            case ev: PrivateEvent =>
              log.debug(s"$clock: triggering timer ${ev.payload.getClass.getSimpleName} for ${ev.affectedAgent}")
              val agent = unsafeGetAgent(ev.affectedAgent, ev)
              val processingResult: MsgHandlingResult[R] = agent.handlePrivateEvent(ev)
              applyEventProcessingResultToSimState(ev.affectedAgent, processingResult)
          }
      }
    }

    log.debug("simulation ended successfully")
  }

  def applyEventProcessingResultToSimState(processingAgentId: AgentRef, processingResult: MsgHandlingResult[R]): Unit = {
    log.debug(s"$clock: appending to queue: ${processingResult.outgoingMessages}")
    for (item <- processingResult.outgoingMessages) {
      val sendingTimepoint = clock + item.relativeTimeOfSendingThisMessage

      item match {
        case Agent.OutgoingMsgEnvelope.Tell(destination, relativeTime, payload) =>
          val networkDelay: Option[TimeDelta] = networkBehavior.calculateUnicastDelay(item.payload, processingAgentId, destination, sendingTimepoint)
          networkDelay match {
            case None =>
              //network failed to deliver the message hence we simply give up (= forget about the message)
            case Some(delay) =>
              val msg = AgentToAgentMsg(
                id = nextMsgId(),
                source = processingAgentId,
                destination,
                sentTime = sendingTimepoint,
                scheduledDeliveryTime = sendingTimepoint + networkBehavior.networkLatencyLowerBound + delay,
                item.payload
              )
              queue.enqueue(msg)
          }

        case Agent.OutgoingMsgEnvelope.Private(relativeTime, deliveryDelay, payload) =>
          val event = PrivateEvent(
              id = nextMsgId(),
              processingAgentId,
              sendingTimepoint + deliveryDelay,
              payload
            )
          queue.enqueue(event)
      }
    }
  }

  private def privateRegisterAgent(agent: Agent[R]): AgentRef = {
    require(! label2Agent.contains(agent.label))
    val agentRef: AgentRef = AgentRefImpl(this.nextAgentId())
    agent.initRef(agentRef)
    agent.initContext(sharedContext)
    agentsRegistry += (agentRef -> agent)
    label2Agent += (agent.label -> agent)
    return agentRef
  }

  private[this] def unsafeGetAgent(agentId: AgentRef, event: SimEventsQueueItem): Agent[R] =
    agentsRegistry.get(agentId) match {
      case Some(x) => x
      case None => throw new RuntimeException(s"unknown agent id $agentId encountered when processing event: $event")
    }


  private def nextAgentId(): Int = agentIdGenerator.next()

  private def nextMsgId(): Long = msgIdGenerator.next()

}

