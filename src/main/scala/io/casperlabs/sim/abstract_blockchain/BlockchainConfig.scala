package io.casperlabs.sim.abstract_blockchain

import io.casperlabs.sim.blockchain_components.execution_engine.{Account, Ether, Gas}

trait BlockchainConfig {

  //################################ Execution engine cost params #################################

  def accountCreationCost: Gas
  def transferCost: Gas
  def successfulBondingCost: Gas
  def refusedBondingCost: Gas
  def successfulUnbondingCost: Gas
  def refusedUnbondingCost: Gas
  def slashingCost: Gas

  //################################ PoS economic params ##########################################

  def bondingDelay: Gas
  def unbondingDelay: Gas
  def maxStake: Ether
  def minStake: Ether
  def minBondingUnbondingRequest: Ether
  def maxBondingRequest: Ether
  def maxUnbondingRequest: Ether
  def bondingSlidingWindowSize: Gas
  def unbondingSlidingWindowSize: Gas
  def bondingTrafficAsNumberOfRequestsLimit: Int
  def bondingTrafficAsStakeDifferenceLimit: Ether
  def unbondingTrafficAsNumberOfRequestsLimit: Int
  def unbondingTrafficAsStakeDifferenceLimit: Ether

  //################################ Block rewards params ##########################################

  def pTimeLimitForClaimingBlockReward: Gas

}
