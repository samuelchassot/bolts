import ch.epfl.chassot.MutableLongMap
import ch.epfl.chassot.ListMapLongKey
import stainless.collection.List
import benchmark.BenchmarkUtil.*
import scala.util.Random
import hashconsing.A

object Main extends App {
  def main(): Unit = {
    // val mTest = MutableLongMap.getEmptyLongMap[Long](k => 0L)

    var mTest = ListMapLongKey[Long](List[(Long, Long)]())

    val nKeys = (2048 * 8).toString()
    val t1 = System.nanoTime()
    // val map = getOriginalMapEmptyBuffer(16)
    val map = getVerifiedMapEmptyBuffer(16)
    for (k, v) <- random2to15Pairs do map.update(k, v)
    end for

    var i1 = 0
    val n1 = 24576
    while (i1 < n1) do
      map.remove(randomArrayOfKeysSize2to16(i1))
      i1 += 1
    end while

    for (k, v) <- random2to15Pairs do map.update(k, v)
    end for

    var i = 0
    val n = nKeys.toInt
    while (i < n) do
      map(randomArrayOfKeysSize2to16(i))
      i += 1
    end while
    val t2 = System.nanoTime()
    // val res = keys.map(k => (mTest(k) == k)).reduce(_ && _)
    println(f"Time to run ${(t2 - t1) / 1e6}%.2f ms")
    // println(f"Result valiity: $res")
  }

  def hashConsingTest(): Unit = {
    def nextString(): String = (1 to 4).map(_ => Random.nextPrintableChar()).mkString

    val values = (1 to 32).map(i => (nextString(), Random.nextInt(), Random.nextInt(), Random.nextLong()))
    val firstBatch = values.map((s, x, y, v) => ((s, x, y, v), A.getNewA(s, x, y, v)))
    assert(firstBatch.forall((k, v) => v.s == k._1 && v.x == k._2 && v.y == k._3 && v.v == k._4))
    val sndBatch = Random.shuffle(values).map((s, x, y, v) => ((s, x, y, v), A.getNewA(s, x, y, v)))
    assert(sndBatch.forall((k, v) => v.s == k._1 && v.x == k._2 && v.y == k._3 && v.v == k._4))

    assert(firstBatch.forall(
      (k, inst) => 
        sndBatch.find((k2, _) => k._1 == k2._1 && k._2 == k2._2 && k._3 == k2._3 && k._4 == k2._4).get._2
        == inst))
  }
  hashConsingTest()
}
