package io.casperlabs.sim.tmp

import akka.util.ByteString


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
