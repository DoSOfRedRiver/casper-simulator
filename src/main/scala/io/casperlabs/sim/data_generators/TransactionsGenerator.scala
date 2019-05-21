package io.casperlabs.sim.data_generators

import io.casperlabs.sim.abstract_blockchain.ValidatorId
import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.execution_engine.{Account, Ether, Transaction}
import io.casperlabs.sim.blockchain_components.hashing.FakeSha256Digester
import io.casperlabs.sim.statistics.PseudoGaussianSelectionFromLongInterval

import scala.collection.mutable
import scala.util.Random

/**
  * Generates random transactions.
  * This is for generating traffic of blockchain clients.
  *
  * Caution: we only generate transactions (= deploys) here.
  * The decisions on when and to which node a transaction should be delivered is going to happen
  * in a different layer (see ClientsTrafficGenerator).
  */
class TransactionsGenerator[P, MS, CS <: ComputingSpace[P, MS]](
                                                                 random: Random,
                                                                 gasPriceInterval: (Ether, Ether),
                                                                 computingSpaceProgramsGenerator: ProgramsGenerator[P, MS, CS],
                                                                 startingIdForNewAccounts: Int,
                                                                 preExistingAccounts: Map[Account, Ether],
                                                                 genesisActiveValidators: Map[ValidatorId, Account],
                                                                 genesisPassiveValidators: Map[ValidatorId, Account],
                                                                 allowedBondingRange: (Ether, Ether),
                                                                 allowedUnbondingRange: (Ether, Ether)
                                                               ) {

  val transactionTypeSelector = new RandomSelector[String](
    random,
    freqMap = Map(
      "account-creation" -> 2,
      "ether-transfer" -> 200,
      "smart-contract-exec" -> 300
//      "initial-bonding" -> 1
    ),
  )

  val accountBalances: mutable.Map[Account, Ether] = new mutable.HashMap[Account, Ether]() //todo: fix updating account balances
  accountBalances ++= preExistingAccounts
  val accountNonces: mutable.Map[Account, Long] = new mutable.HashMap[Account, Long]()
  accountNonces ++= preExistingAccounts.mapValues(x => 0)
  var lastAccountId: Int = startingIdForNewAccounts
  val gasPriceGenerator = new PseudoGaussianSelectionFromLongInterval(random, gasPriceInterval)
  val transactionHashGenerator = new FakeSha256Digester(random)
  val activeValidators: mutable.Map[ValidatorId, Account] = new mutable.HashMap[Account, ValidatorId] ++= genesisActiveValidators
  val passiveValidators: mutable.Map[ValidatorId, Account] = new mutable.HashMap[Account, ValidatorId] ++= genesisPassiveValidators

  def createTransaction(): Option[Transaction] = {
    val txHash = transactionHashGenerator.generateHash()

    transactionTypeSelector.next() match {
      case "account-creation" =>
        lastAccountId += 1
        val newAccount: Account = lastAccountId
        accountBalances.put(newAccount, 0L)
        accountNonces.put(newAccount, 0L)
        val sponsor: Account = pickRandomAccount
        return Some(Transaction.AccountCreation(txHash, takeNonceFor(sponsor), sponsor, gasPriceGenerator.next(), gasLimit = 100, newAccount))

      case "ether-transfer" =>
        val sourceAccount = pickRandomAccountWithNonzeroBalance
        val targetAccount = pickRandomAccountAvoiding(sourceAccount)
        val amount: Ether = (accountBalances(sourceAccount) * random.nextDouble()).toLong + 1
        accountBalances(sourceAccount) -= amount
        accountBalances(sourceAccount) += amount
        return Some(Transaction.EtherTransfer(txHash, takeNonceFor(sourceAccount), sourceAccount, gasPriceGenerator.next(), gasLimit = 100, targetAccount, amount))

      case "smart-contract-exec" =>
        val sponsor: Account = pickRandomAccount
        val (program, gasLimit) = computingSpaceProgramsGenerator.next()
        return Some(Transaction.SmartContractExecution(txHash, takeNonceFor(sponsor), sponsor, gasPriceGenerator.next(), gasLimit, program))

//      case "initial-bonding" =>
//        if (passiveValidators.nonEmpty) {
//          val vid: ValidatorId = pickRandomPassiveValidator
//          val sponsor: Account = passiveValidators(vid)
//          return Some(Transaction.Bonding(txHash, takeNonceFor(sponsor), sponsor, gasPriceGenerator.next(), gasLimit = 200, vid, allowedBondingRange._1 + random.nextInt(200)))
//        } else
//          return None

//      case "bonding" =>
//        //todo: fix this when the finalized blocks data stream is available
//
//      case "unbonding" =>
//        //todo

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

  def pickRandomActiveValidator: ValidatorId = {
    val currentSnapshot = activeValidators.keys.toSeq
    return currentSnapshot(random.nextInt(currentSnapshot.length))
  }

  def pickRandomPassiveValidator: ValidatorId = {
    val currentSnapshot = passiveValidators.keys.toSeq
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
