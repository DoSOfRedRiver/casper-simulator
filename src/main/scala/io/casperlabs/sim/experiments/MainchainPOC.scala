package io.casperlabs.sim.experiments

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, BlockchainSimulationOutputItem, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace.StatementCode
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.discovery.TrivialDiscovery
import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.gossip.NaiveGossip
import io.casperlabs.sim.blockchain_components.hashing.FakeSha256Digester
import io.casperlabs.sim.blockchain_components.network_models.UniformNetwork
import io.casperlabs.sim.blockchain_models.casperlabs_classic._
import io.casperlabs.sim.data_generators.{BinaryArraySpaceProgramsGenerator, ClientsTrafficGenerator, TransactionsGenerator}
import io.casperlabs.sim.sim_engine_sequential.SimulationImpl
import io.casperlabs.sim.simulation_framework.SimEventsQueueItem.ExternalEvent
import io.casperlabs.sim.simulation_framework._
import io.casperlabs.sim.statistics.GaussDistributionParams

import scala.util.Random

/**
  * Casper-Mainchain first experiment (proof-of-concept).
  * We use hardcoded configuration here.
  */
object MainchainPOC {

  def main(args: Array[String]): Unit = {
    if (args.length != 4) {
      println(s"Expected are exactly 4 command-line arguments:")
      println(s"    number of validators [integer]")
      println(s"    number of validators bonded at genesis [integer]")
      println(s"    simulation length time unit [enumeration: sec, min, hou, day]")
      println(s"    simulation length [integer")
      System.exit(1)
    }

    val numberOfValidators = args(0).toInt
    val numberOfValidatorsBondedAtGenesis = args(1).toInt
    val simulationEnd = args(2) match {
      case "sec" => Timepoint.seconds(args(3).toLong)
      case "min" => Timepoint.minutes(args(3).toLong)
      case "hou" => Timepoint.hours(args(3).toLong)
      case "day" => Timepoint.days(args(3).toLong)
    }

    this.launchSimulation(numberOfValidators, numberOfValidatorsBondedAtGenesis, simulationEnd)
  }

  //###################################### BLOCKCHAIN CONFIG ##################################

  val initialEtherPerGenesisValidator: Ether = 1000 * 1000
  val initialStakePerGenesisValidator: Ether = 1000
  val blockProposeDelay: TimeDelta = seconds(5)

  val blockchainConfig: BlockchainConfig = new BlockchainConfig {

    val accountCreationCost: Gas = 10
    val transferCost: Gas = 2
    val successfulBondingCost: Gas = 20
    val refusedBondingCost: Gas = 2
    val successfulUnbondingCost: Gas = 20
    val refusedUnbondingCost: Gas = 2
    val slashingCost: Gas = 1

    val bondingDelay: Gas = 200
    val unbondingDelay: Gas = 100
    val maxStake: Ether = 1000
    val minStake: Ether = 5
    val minBondingUnbondingRequest: Ether = 50
    val maxBondingRequest: Ether = 500
    val maxUnbondingRequest: Ether = 2000
    val bondingSlidingWindowSize: Gas = 500
    val unbondingSlidingWindowSize: Gas = 500
    val bondingTrafficAsNumberOfRequestsLimit: Int = 5
    val bondingTrafficAsStakeDifferenceLimit: Ether =  500
    val unbondingTrafficAsNumberOfRequestsLimit: Int = 5
    val unbondingTrafficAsStakeDifferenceLimit: Ether = 800

    val pTimeLimitForClaimingBlockReward: Gas = 300
  }

  //####################### EXECUTION ENGINE & BLOCKS EXECUTOR INIT #################################

  type MS = BinaryArraySpace.MemoryState
  type P = BinaryArraySpace.Program
  type CS = ComputingSpace[P,MS]
  type GS = GlobalState[MS]

  val computingSpace = BinaryArraySpace.ComputingSpace
  val initialMemoryState: MS = computingSpace.initialState
  val executionEngine = new DefaultExecutionEngine(blockchainConfig, computingSpace)
  val blocksExecutor = new CasperMainchainBlocksExecutor[CS,P,MS](executionEngine, blockchainConfig)
  val sharedSourceOfRandomness = new Random(42)


  def label(id: Int): String = s"validator-$id"

  def millis(n: Long): TimeDelta = n * 1000

  def seconds(n: Long): TimeDelta = n * 1000000

  private def launchSimulation(numberOfValidators: Int, numberOfValidatorsBondedAtGenesis: Int, simulationEnd: Timepoint): Unit = {

    //########################################## GENESIS ##############################################

    val accountIds: IndexedSeq[Account] = (0 until numberOfValidators).toIndexedSeq
    val validators : IndexedSeq[ValidatorId] = (0 until numberOfValidators).map(vid => vid + 100).toIndexedSeq
    val mapOfGenesisAccounts = accountIds.map(a => (a, AccountState(0, initialEtherPerGenesisValidator))).toMap
    val accountsRegistry = new AccountsRegistry(mapOfGenesisAccounts)

    val mapOfGenesisValidators =
      for {
        i <- 0 until numberOfValidatorsBondedAtGenesis
        state =
          if (i <= numberOfValidatorsBondedAtGenesis)
            ValidatorState.initial(validators(i), accountIds(i), initialStakePerGenesisValidator)
          else
            ValidatorState.initial(validators(i), accountIds(i), stake = 0)
      }
        yield validators(i) -> state

    val validatorsBook = ValidatorsBook.genesis(mapOfGenesisValidators.toMap)
    val genesisGlobalState = GlobalState(initialMemoryState, accountsRegistry, validatorsBook)
    val genesisPostStateHash = executionEngine.globalStateHash(genesisGlobalState)

    val genesisBlock = Block.generateGenesisBlock(
      magicWord = "casperlabs",
      genesisGlobalState.validatorsBook.validatorWeightsMap,
      genesisPostStateHash
    )

    //################################ CLIENT TRAFFIC GENERATOR ########################################

    val programsGenerator = new BinaryArraySpaceProgramsGenerator(
      sharedSourceOfRandomness,
      programSizeRange = GaussDistributionParams(20,10),
      memorySize = 1000,
      statementsFrequencyTable = Map(
        StatementCode.addToAcc -> 1.5,
        StatementCode.assert -> 0.002,
        StatementCode.branch -> 0.3,
        StatementCode.loop -> 0.05,
        StatementCode.clearAcc -> 1,
        StatementCode.exit -> 0.01,
        StatementCode.flip -> 1,
        StatementCode.nop -> 0.01,
        StatementCode.storeAcc -> 1,
        StatementCode.write -> 1,
      ),
      entanglementFactor = 0.5,
      gasLimitToProgramSizeFactor = 2.0
    )

    val transactionsGenerator = new TransactionsGenerator(
      sharedSourceOfRandomness,
      gasPriceInterval = (10, 50),
      computingSpaceProgramsGenerator = programsGenerator,
      startingIdForNewAccounts = 1000,
      mapOfGenesisAccounts.mapValues(state => state.balance)
    )

    //################################### SIMULATION #############################################################

    val network = new UniformNetwork(
      sharedSourceOfRandomness,
      minDelay = millis(20),
      maxDelay = millis(500),
      dropRate = 0.0
    )

    val fakeSha256Digester = new FakeSha256Digester(sharedSourceOfRandomness)
    val id2NodeId: Map[Int, NodeId] = (0 until numberOfValidators).map(i => (i,fakeSha256Digester.generateHash())).toMap
    val nodeId2id: Map[NodeId, Int] = id2NodeId map { case (k,v) => (v,k) }
    val nodeId2agentLabel: Map[NodeId, String] = nodeId2id.mapValues(id => label(id))
    val globalStatesStorage = new GlobalStatesStorage(executionEngine)

    def buildNewValidatorNode(id: Int): ValidatorNode = {
      val thisNodeId: NodeId = id2NodeId(id)
      val discoveryPlugin = new TrivialDiscovery(thisNodeId, nodeId2agentLabel - thisNodeId)
      val gossipPlugin = new NaiveGossip(discoveryPlugin)

      val validatorPlugin: Validator = new Validator(
        blockchainConfig,
        validatorId = validators(id),
        genesisBlock,
        genesisGlobalState,
        gossipPlugin,
        blockProposeDelay,
        globalStatesStorage
      )

      new ValidatorNode(
        blockchainConfig,
        nodeId = fakeSha256Digester.generateHash(),
        label = label(id),
        genesisBlock,
        discoveryPlugin,
        gossipPlugin,
        validatorPlugin
      )
    }

    val simulation: Simulation[BlockchainSimulationOutputItem] = new SimulationImpl[BlockchainSimulationOutputItem](simulationEnd, network)
    val agents: IndexedSeq[Agent[BlockchainSimulationOutputItem]] = (0 until numberOfValidators) map { i => buildNewValidatorNode(i) }
    val agentRefs: IndexedSeq[AgentRef] = agents.map(agent => simulation.preRegisterAgent(agent))

    val trafficGenerator = new ClientsTrafficGenerator(
      sharedSourceOfRandomness,
      trafficPerNode = Map.empty, //todo make this part of the config once the support on ClientsTrafficGenerator's side is fixed
      initialAccounts = accountIds.map(account => account -> initialEtherPerGenesisValidator).toMap,
      agentRefs,
      deploysPerSecond = 1,
      transactionsGenerator
    )

    val agentsCreationStream = Iterator.empty

    val externalEventsStream: Iterator[ExternalEvent] = new Iterator[ExternalEvent] {
      override def hasNext: Boolean = true

      override def next(): ExternalEvent = {
        val nextScheduledDeploy = trafficGenerator.next()
        return ExternalEvent(
          nextScheduledDeploy.id,
          affectedAgent = nextScheduledDeploy.node,
          scheduledDeliveryTime = nextScheduledDeploy.deliveryTimepoint,
          payload = nextScheduledDeploy.transaction
        )
      }
    }

    simulation.start(externalEventsStream, agentsCreationStream)

  }

}
