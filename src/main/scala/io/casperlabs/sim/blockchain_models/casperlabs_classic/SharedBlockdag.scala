package io.casperlabs.sim.blockchain_models.casperlabs_classic

case class SharedBlockdag(parentsInverseMap: Map[Block, Set[Block]], justificationsInverseMap: Map[Block, Set[Block]]) {

  def addBlock(block: NormalBlock): SharedBlockdag = ???
//    val updatedParentsInverseMap = block.parents.foldLeft(parentsInverseMap) { case (map, parent) => BlockdagUtils.addArrowInBlocksMultimap(map, parent, block)}
//    val updatedJustificationsInverseMap = block.justifications.foldLeft(justificationsInverseMap) { case (map, parent) => BlockdagUtils.addArrowInBlocksMultimap(map, parent, block)}
//    return SharedBlockdag(updatedParentsInverseMap, updatedJustificationsInverseMap)

}
