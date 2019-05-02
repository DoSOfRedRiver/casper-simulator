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

  private var memoizedHash: Option[HashValue] = None

  /**
    * Plays the role of transaction's hash, although for the needs of simulation we are faking the 'real' hash with something that is easier to calculate.
    */
  def hash: HashValue = memoizedHash getOrElse {
    val h = FakeHashGenerator.nextHash()
    memoizedHash = Some(h)
    h
  }
}

object Transaction {

  case class AccountCreation(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, newAccount: Account) extends Transaction {
  }

  case class SmartContractExecution[CS <: ComputingSpace[P,MS], P, MS](nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, program: P) extends Transaction {
  }

  case class EtherTransfer(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, targetAccount: Account, value: Ether) extends Transaction {
  }

  case class Bonding(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class Unbonding(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, validator: ValidatorId, value: Ether) extends Transaction {
  }

  case class EquivocationSlashing(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, victim: ValidatorId, evidence: (BlockId, BlockId)) extends Transaction {
  }

}




