package io.casperlabs.sim.blockchain_components.execution_engine

import io.casperlabs.sim.abstract_blockchain.{BlockId, ValidatorId}
import io.casperlabs.sim.blockchain_components.hashing.Hash

/**
  * Transaction (to be executed) on the blockchain computer.
  */
sealed abstract class Transaction {
  def id: Hash
  lazy val shortId: String = id.toString.take(8)
  def sponsor: Account
  def gasPrice: Ether
  def gasLimit: Gas
  def nonce: Long

  //we override here default equals implementation for case classes
  override def equals(obj: Any): Boolean =
    obj match {
      case t: Transaction => this.id == t.id
      case _ => false
    }

  override def hashCode(): Int = id.hashCode
}

object Transaction {

  case class AccountCreation(id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, newAccount: Account) extends Transaction {
  }

  case class SmartContractExecution[P, MS](id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, program: P) extends Transaction {
  }

  case class EtherTransfer(id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, targetAccount: Account, value: Ether) extends Transaction {
  }

  case class Bonding(id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class Unbonding(id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class EquivocationSlashing(id: Hash, nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, victim: ValidatorId, evidence: (BlockId, BlockId)) extends Transaction {
  }

}




