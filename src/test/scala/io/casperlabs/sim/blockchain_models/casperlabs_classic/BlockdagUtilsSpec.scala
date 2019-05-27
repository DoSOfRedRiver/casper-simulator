package io.casperlabs.sim.blockchain_models.casperlabs_classic

import io.casperlabs.sim.blockchain_components.execution_engine.Ether
import io.casperlabs.sim.blockchain_components.graphs.DoublyLinkedDag
import io.casperlabs.sim.blockchain_models.casperlabs_classic.BlockdagUtilsSpec._
import org.scalatest.{FlatSpec, Matchers}

class BlockdagUtilsSpec extends FlatSpec with Matchers {

  "lmdScoring" should "propagate fixed weights on a tree" in {
    /* The DAG looks like:
     * 
     *
     *   d  e   f   
     *    \ /   |   
     *     a    b   c
     *      \   |   /
     *       genesis
     */

    val weightsMap = Map[Char, Ether](
      'A' -> 3,
      'B' -> 5,
      'C' -> 7
    )

    val genesis = MockBlock.genesis(weightsMap)
    val a = MockBlock(1, Vector(genesis), weightsMap)
    val b = MockBlock(2, Vector(genesis), weightsMap)
    val c = MockBlock(3, Vector(genesis), weightsMap)
    val d = MockBlock(4, Vector(a), weightsMap)
    val e = MockBlock(5, Vector(a), weightsMap)
    val f = MockBlock(6, Vector(b), weightsMap)

    val latestMessages = Map(
      'A' -> d,
      'B' -> e,
      'C' -> f
    )

    val scores = BlockdagUtils.lmdScoring(
      latestMessages,
      constWeights(weightsMap),
      dagFromTips(Vector(d, e, f))
    )

    scores shouldEqual Map(
      genesis -> (3 + 5 + 7),
      a -> (3 + 5),
      b -> 7,
      d -> 3,
      e -> 5,
      f -> 7
    )
  }

  it should "propagate fixed weights on a DAG" in {
    /* The DAG looks like:
     * 
     *        i
     *        |
     *        g   h
     *       | \ /  \
     *       d  e   f
     *      / \/   /   
     *     a  b   c
     *      \ |  / 
     *       genesis
     */

    val weightsMap = Map[ValidatorId, Ether](
      'A' -> 3,
      'B' -> 5,
      'C' -> 7
    )

    val genesis = MockBlock.genesis(weightsMap)
    val a = MockBlock(1, Vector(genesis), weightsMap)
    val b = MockBlock(2, Vector(genesis), weightsMap)
    val c = MockBlock(3, Vector(genesis), weightsMap)
    val d = MockBlock(4, Vector(a, b), weightsMap)
    val e = MockBlock(5, Vector(b), weightsMap)
    val f = MockBlock(6, Vector(c), weightsMap)
    val g = MockBlock(7, Vector(d, e), weightsMap)
    val h = MockBlock(8, Vector(e, f), weightsMap)
    val i = MockBlock(9, Vector(g), weightsMap)

    val latestMessages = Map(
      'A' -> g,
      'B' -> h,
      'C' -> i
    )

    val scores = BlockdagUtils.lmdScoring(
      latestMessages,
      constWeights(weightsMap),
      dagFromTips(Vector(i, h, g))
    )

    scores shouldEqual Map[MockBlock, Ether](
      genesis -> (3 + 5 + 7),
      a -> (3 + 7),
      b -> (3 + 5 + 7),
      c -> 5,
      d -> (3 + 7),
      e -> (3 + 5 + 7),
      f -> 5,
      g -> (3 + 7),
      h -> 5,
      i -> 7
    )
  }

  // TODO: add tests for variable weights

  "lmdMainchainGhost" should "pick the correct fork choice tip" in {
    /* The DAG looks like:
     * 
     *        i
     *        |
     *        g   h
     *       | \ /  \
     *       d  e   f
     *      / \/   /   
     *     a  b   c
     *      \ |  / 
     *       genesis
     */

    val weightsMap = Map[ValidatorId, Ether](
      'A' -> 3,
      'B' -> 5,
      'C' -> 7
    )

    val genesis = MockBlock.genesis(weightsMap)
    val a = MockBlock(1, Vector(genesis), weightsMap)
    val b = MockBlock(2, Vector(genesis), weightsMap)
    val c = MockBlock(3, Vector(genesis), weightsMap)
    val d = MockBlock(4, Vector(a, b), weightsMap)
    val e = MockBlock(5, Vector(b), weightsMap)
    val f = MockBlock(6, Vector(c), weightsMap)
    val g = MockBlock(7, Vector(d, e), weightsMap)
    val h = MockBlock(8, Vector(e, f), weightsMap)
    val i = MockBlock(9, Vector(g), weightsMap)

    val latestMessages = Map(
      'A' -> g,
      'B' -> h,
      'C' -> i
    )

    val tip = BlockdagUtils.lmdMainchainGhost(
      latestMessages,
      constWeights(weightsMap),
      dagFromTips(Vector(i, h, g)),
      genesis,
      (b: MockBlock) => b.id
    )

    tip shouldEqual i
  }

  // TODO: add test for DAG-based LMD GHOST
}

object BlockdagUtilsSpec {
  type ValidatorId = Char
  type Weight = Ether

  case class MockBlock(id: Int, parents: IndexedSeq[MockBlock], weights: Map[ValidatorId, Weight]) {
    override def toString: String = s"MockBlock($id)"
  }
  object MockBlock {
    def genesis(weights: Map[ValidatorId, Weight]): MockBlock = MockBlock(0, Vector.empty, weights)
  }

  def dagFromTips(tips: IndexedSeq[MockBlock]): DoublyLinkedDag[MockBlock] = {
    val allBlocks = DoublyLinkedDag.bfTraverse[MockBlock](tips, _.parents).foldLeft(List.empty[MockBlock]) {
      case (acc, block) => block :: acc
    }

    val genesis = allBlocks.head
    val dag = DoublyLinkedDag.prePointerDag[MockBlock](genesis, _.parents)
    allBlocks.tail.foreach(block => dag.insert(block, block.parents) match {
      case DoublyLinkedDag.InsertResult.AlreadyInserted() => ()
      case DoublyLinkedDag.InsertResult.Success(doInsert) => doInsert()
      case DoublyLinkedDag.InsertResult.MissingTargets(missing) => throw new RuntimeException(s"Missing targets: $missing")
    })

    dag
  }

  def constWeights(weights: Map[ValidatorId, Weight]): (MockBlock, ValidatorId) => Weight = 
    (_: MockBlock, v: ValidatorId) => weights(v)
}
