package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.blockchain_components.computing_spaces.ComputingSpace
import io.casperlabs.sim.blockchain_components.hashing.{FakeHashGenerator, HashValue}

/**
  * Transaction (to be executed) on the blockchain computer.
  */
sealed abstract class Transaction {
  def sponsor: Account
  def gasPrice: Ether
  def gasLimit: Gas
  def nonce: Long
  def hash: HashValue
}

object Transaction {

  case class AccountCreation(hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, newAccount: Account) extends Transaction {
  }

  case class SmartContractExecution[P, MS](hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, program: P) extends Transaction {
  }

  case class EtherTransfer(hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, targetAccount: Account, value: Ether) extends Transaction {
  }

  case class Bonding(hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class Unbonding(hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class EquivocationSlashing(hash: HashValue, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, victim: ValidatorId, evidence: (BlockId, BlockId)) extends Transaction {
  }

}




