// to run it:
// scala-cli -J -Xmx24576m Benchmark.scala MutableLongMap.scala StrictlyOrderedLongListMap.scala  $(find /localhome/chassot/stainless/frontends/library/stainless/ -name "*.scala")

import ch.epfl.chassot.MutableLongMap.*
import scala.collection.mutable.LongMap

object MutableLongMapBenchmark {
  def benchmarkVerifiedMap(n: Int, initialArraySize: Int): (Boolean, Long) = {
    val m = getEmptyLongMap(l => -1L, initialArraySize)
    // val n = Math.pow(2, 10).toInt
    val t1 = System.nanoTime()
    for (i <- 0 to n) {
      val res = m.update(i, i)
    }
    val t2 = System.nanoTime()

    val l = for i <- 0L to n yield (m(i) == i)
    val res = l.foldLeft(true)((acc, b) => acc && b)

    (res, t2 - t1)
  }

  def benchmarkOriginalMap(n: Int, initialArraySize: Int): (Boolean, Long) = {
    val m = new LongMap[Long](initialArraySize)
    // val n = Math.pow(2, 10).toInt
    val t1 = System.nanoTime()
    for (i <- 0 to n) {
      val res = m.update(i, i)
    }
    val t2 = System.nanoTime()

    val l = for i <- 0L to n yield (m(i) == i)
    val res = l.foldLeft(true)((acc, b) => acc && b)
    (res, t2 - t1)
  }
}

@main def main(): Unit = {
  val n = Math.pow(2, 5).toInt
  val initialArraySize = 16
  val warmupIterationsNumber = 5
  println("Running bechmark with the following parameters:")
  println(f"\tNumber of elements to add: $n")
  println(f"\tInitial array size: $initialArraySize")
  println(f"\tNumber of warmup iterations: $warmupIterationsNumber")

  println("Warming up verified map...")
  for (i <- 0 to warmupIterationsNumber) {
    println(f"Warmup iteration $i")
    MutableLongMapBenchmark.benchmarkVerifiedMap(n, initialArraySize)
  }
  println("Done warmup")
  println("Running benchmark for verified map...")
  val (correctVerified, timeVerified) = MutableLongMapBenchmark.benchmarkVerifiedMap(n, initialArraySize)

  println("Warming up original map...")
  for (i <- 0 to warmupIterationsNumber) {
    println(f"Warmup iteration $i")
    MutableLongMapBenchmark.benchmarkOriginalMap(n, initialArraySize)
  }
  println("Done warmup")
  println("Running benchmark for original map...")
  val (correctOriginal, timeOriginal) = MutableLongMapBenchmark.benchmarkOriginalMap(n, initialArraySize)

  if (!correctVerified) {
    println("ERROR: verified map is not correct")
    return
  }
  if (!correctOriginal) {
    println("ERROR: original map is not correct")
    return
  }
  println(f"Time to insert $n elements in verified map: $timeVerified μs = ${timeVerified.toDouble / 1000} ms = ${timeVerified.toDouble / 1000000} s")
  println(f"Time to insert $n elements in original map: $timeOriginal μs = ${timeOriginal.toDouble / 1000} ms = ${timeOriginal.toDouble / 1000000} s")
  println(f"Ratio original/verified: ${timeOriginal.toDouble / timeVerified.toDouble}")
}
