package io.casperlabs.sim.blockchain_components.execution_engine

case class BondingQueueItem(validator: NodeId, amount: Ether, requestTime: Gas) {
}
