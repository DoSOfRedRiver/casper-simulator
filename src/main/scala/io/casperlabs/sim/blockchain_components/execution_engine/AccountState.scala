package io.casperlabs.sim.blockchain_components.execution_engine

case class AccountState(nonce: Long, balance: Ether)

object AccountState {
  val empty: AccountState = AccountState(0,0)
}
