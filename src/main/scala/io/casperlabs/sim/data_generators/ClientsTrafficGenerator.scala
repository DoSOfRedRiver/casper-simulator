package io.casperlabs.sim.data_generators

import io.casperlabs.sim.abstract_blockchain.ScheduledDeploy
import io.casperlabs.sim.blockchain_components.computing_spaces.BinaryArraySpace.StatementCode
import io.casperlabs.sim.blockchain_components.computing_spaces.{BinaryArraySpace, ComputingSpace}
import io.casperlabs.sim.blockchain_components.execution_engine.{Account, Ether, Transaction}
import io.casperlabs.sim.simulation_framework.{AgentRef, Timepoint}

import scala.collection.mutable
import scala.util.Random

/**
  * Simulates the traffic coming to the blockchain network from clients.
  * This is modelled as a stream of ScheduledDeploy instances.
  *
  * This is a simple generator that does not support attack scenarios.
  * We generate
  */
class ClientsTrafficGenerator[P, MS, CS <: ComputingSpace[P, MS]](
                                                                   random: Random,
                                                                   initialAccounts: Map[Account, Ether],
                                                                   nodes: IndexedSeq[AgentRef],
                                                                   deploysPerSecond: Double,
                                                                   transactionsGenerator: TransactionsGenerator[P, MS, CS]
                                                                 ) {


  val transactionTypeSelector = new RandomSelector[String](
    freqMap = Map("account-creation" -> 1, "ether-transfer" -> 200, "smart-contract-exec" -> 300),
    random
  )

  val accountBalances: mutable.Map[Account, Ether] = new mutable.HashMap[Account, Ether]()
  val accountNonces: mutable.Map[Account, Long] = new mutable.HashMap[Account, Long]()
  var lastAccountId: Int = 1000
  val gasPrice: Ether = 1
  val gasLimit: Ether = 200
  val lambda: Double = deploysPerSecond / 1000000 //rescaling to microseconds
  var clock: Long = 0L

  val cs = BinaryArraySpace.ComputingSpace

  val programsGenerator = new BinaryArraySpaceProgramsGenerator(
    random = new Random,
    averageLength = 20,
    standardDeviation = 10,
    memorySize = 200,
    frequenciesOfStatements = Map(
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
    entanglementFactor = 0.5
  )

  def next(): ScheduledDeploy = {
    val targetNode = nodes(random.nextInt(nodes.length))
    val when = nextPoissonTimepoint()
    val transaction = createTransaction()

    return ScheduledDeploy(transaction, when, targetNode)
  }

  def nextPoissonTimepoint(): Timepoint = {
    val point: Double = - math.log(random.nextDouble()) / lambda
    clock = clock + point.toLong
    return Timepoint(clock)
  }

  def createTransaction(): Transaction = {
    transactionTypeSelector.next() match {
      case "account-creation" =>
        lastAccountId += 1
        val newAccount: Account = lastAccountId
        accountBalances.put(newAccount, 0L)
        accountNonces.put(newAccount, 0L)
        val sponsor: Account = pickRandomAccount
        return Transaction.AccountCreation(takeNonceFor(sponsor), sponsor, gasPrice, gasLimit, newAccount)

      case "ether-transfer" =>
        val sourceAccount = pickRandomAccountWithNonzeroBalance
        val targetAccount = pickRandomAccountAvoiding(sourceAccount)
        val amount: Ether = (accountBalances(sourceAccount) * random.nextDouble()).toLong + 1
        return Transaction.EtherTransfer(takeNonceFor(sourceAccount), sourceAccount, gasPrice, gasLimit, targetAccount, amount)

      case "smart-contract-exec" =>
        val sponsor: Account = pickRandomAccount
        return Transaction.SmartContractExecution(takeNonceFor(sponsor), sponsor, gasPrice, gasLimit, programsGenerator.next())
    }
  }

  def takeNonceFor(account: Account): Long = {
    val result = accountNonces(account)
    accountNonces(account) = result + 1
    return result
  }

  def pickRandomAccount: Account = {
    val currentSnapshot = accountBalances.keys.toSeq
    return currentSnapshot(random.nextInt(currentSnapshot.length))
  }

  def pickRandomAccountWithNonzeroBalance: Account = {
    val candidates = (accountBalances filter { case (acc, bal) => bal > 0 }).keys.toSeq
    return candidates(random.nextInt(candidates.length))
  }

  def pickRandomAccountAvoiding(thisOne: Account): Account = {
    val currentSnapshot = accountBalances.keys.filterNot(account => account == thisOne).toSeq
    return currentSnapshot(random.nextInt(currentSnapshot.length))
  }

}

