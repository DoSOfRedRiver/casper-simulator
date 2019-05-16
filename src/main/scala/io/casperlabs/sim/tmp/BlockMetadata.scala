package io.casperlabs.sim.tmp


final case class BlockMetadata(
                                blockHash: BlockHash,
                                parents: List[BlockHash],
                                sender: Array[Byte],
                                justifications: List[Justification],
                                weightMap: Map[Validator, Long],
                                blockNum: Long,
                                seqNum: Int
                              ) {
}
