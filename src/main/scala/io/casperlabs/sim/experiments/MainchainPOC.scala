package io.casperlabs.sim.experiments

import io.casperlabs.sim.abstract_blockchain.{BlockchainConfig, NodeId, ValidatorId}
import io.casperlabs.sim.blockchain_components.computing_spaces.{ComputingSpace, MockingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.AccountsRegistry.AccountState
import io.casperlabs.sim.blockchain_components.execution_engine._
import io.casperlabs.sim.blockchain_components.network_models.UniformNetwork
import io.casperlabs.sim.blockchain_models.casperlabs_classic._
import io.casperlabs.sim.sim_engine_sequential.SimulationImpl
import io.casperlabs.sim.simulation_framework._

import scala.util.Random

object MainchainPOC {

  type MS = MockingSpace.MemoryState
  type P = MockingSpace.Program
  type CS = ComputingSpace[P,MS]
  type GS = GlobalState[MS]

  //###################################### BLOCKCHAIN CONFIG ##################################

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

  val computingSpace = MockingSpace.ComputingSpace
  val initialMemoryState: MS = MockingSpace.MemoryState.Singleton
  val ee = new DefaultExecutionEngine(blockchainConfig, computingSpace)
  val blocksExecutor = new CasperMainchainBlocksExecutor[CS,P,MS](ee, blockchainConfig)
  val random = new Random
  val gasPrice: Ether = 1

  //#################################### GENESIS #################################

  val account1: Account = 1
  val account2: Account = 2
  val account3: Account = 3
  val account4: Account = 4
  val account5: Account = 5

  val validator1: ValidatorId = 101
  val validator2: ValidatorId = 102
  val validator3: ValidatorId = 103
  val validator4: ValidatorId = 104
  val validator5: ValidatorId = 105

  val mapOfInitialAccounts = Map (
    account1 -> 100000L,
    account2 -> 100000L,
    account3 -> 100000L,
    account4 -> 100000L,
    account5 -> 100000L
  )

  val accountsRegistry = new AccountsRegistry(mapOfInitialAccounts map { case (k,v) => (k, AccountState(0, v))})


  val validatorsBook = ValidatorsBook.genesis(
    Map(
      validator1 -> ValidatorState.initial(validator1, account1, 500),
      validator2 -> ValidatorState.initial(validator2, account2, 200),
      validator3 -> ValidatorState.initial(validator3, account3, 300),
      validator3 -> ValidatorState.initial(validator3, account3, 500),
      validator5 -> ValidatorState.initial(validator5, account5, 400)
    )
  )

  val genesisGlobalState = GlobalState(initialMemoryState, accountsRegistry, validatorsBook)
  val genesisPostStateHash = ee.globalStateHash(genesisGlobalState)
  val genesisBlock = Block.generateGenesisBlock("casperlabs", genesisGlobalState.validatorsBook.validatorWeightsMap, genesisPostStateHash)

  //################################### SIMULATION #############################################################

  def millis(n: Long): TimeDelta = n * 1000

  def seconds(n: Long): TimeDelta = n * 1000000

  val network = new UniformNetwork(
    random,
    minDelay = millis(20),
    maxDelay = millis(500),
    dropRate = 0.0
  )

//  val agents = (1 to 5) map { i =>  (agentRef: AgentRef) => buildNewValidatorNode(i, agentRef)  }


  def buildNewValidatorNode(nodeId: NodeId, agentRef: AgentRef): ValidatorNode = ???
//    new ValidatorNode(
//      blockchainConfig,
//      nodeId ,
//      agentRef,
//      label = s"node-$nodeId",
//      genesisBlock,
//      discovery: Discovery[ValidatorId, AgentRef] with PluggableAgentBehaviour,
//      gossip: Gossip[ValidatorId, AgentRef] with PluggableAgentBehaviour,
//      validator: Validator
//
//    )

  val preexistingAgents = List(

  )

  val simulation = new SimulationImpl(preexistingAgents, Timepoint(seconds(30)), network)

//  val clientTrafficGen = new ClientsTrafficGenerator[P,MS,CS](
//    random,
//    mapOfInitialAccounts,
//
//  )

  //##################################################################################################################################

  def main(args: Array[String]): Unit = {

  }

}
