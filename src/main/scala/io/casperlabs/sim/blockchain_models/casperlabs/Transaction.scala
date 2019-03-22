package io.casperlabs.sim.blockchain_models.casperlabs

sealed abstract class Transaction {
  def sponsor: Account
  def gasPrice: Ether
  def gasLimit: Gas
  def nonce: Long
  def gasUsed: Gas
  def costIfSuccessful: Ether = this.gasUsed * gasPrice
  def costCappedByGasLimit: Ether = math.min(this.gasUsed, this.gasLimit) * gasPrice

  private var memoizedHash: Option[Long] = None

  /**
    * Plays the role of transaction's hash, although for the needs of simulation we are faking the 'real' hash with something that is easier to calculate.
    */
  def hash: Long = memoizedHash getOrElse {
    val h = sponsor * nonce + gasLimit + gasPrice * 13
    memoizedHash = Some(h)
    h
  }
}

object Transaction {

  case class AccountCreation(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, newAccount: Account) extends Transaction {
    override def gasUsed: Gas = 100
  }

  case class DatabaseUpdate(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, operations: Map[Slot, DbAtomicAction]) extends Transaction {
    override def gasUsed: Gas = (operations.values map { case DbAtomicAction.Read => 1; case  DbAtomicAction.Write(n) => 10; case DbAtomicAction.Inc => 3}).sum
  }

  case class EtherTransfer(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, targetAccount: Account, value: Ether) extends Transaction {
    override def gasUsed: Gas = 10
  }

  case class ValidatorInit(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, node: Node) extends Transaction {
    override def gasUsed: Gas = 1000
  }

  case class Bonding(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, node: Node, value: Ether) extends Transaction {
    override def gasUsed: Gas = 100
  }

  case class Unbonding(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, node: Node, value: Ether) extends Transaction {
    override def gasUsed: Gas = 1000
  }

  case class EquivocationSlashing(nonce: Long, sponsor: Account, gasPrice: Ether, gasLimit: Gas, victim: Node, evidence: (NormalBlock, NormalBlock)) extends Transaction {
    override def gasUsed: Gas = 1
  }

  case class BlockCompletion()

}

sealed abstract class DbAtomicAction {

}

object DbAtomicAction {
  case object Read extends DbAtomicAction
  case class Write(n: Int) extends DbAtomicAction
  case object Inc extends DbAtomicAction

}
