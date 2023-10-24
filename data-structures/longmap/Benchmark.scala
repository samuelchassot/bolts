// to run it:
// scala-cli -J -Xmx24576m Benchmark.scala MutableLongMap.scala StrictlyOrderedLongListMap.scala  $(find /localhome/chassot/stainless/frontends/library/stainless/ -name "*.scala")

import ch.epfl.chassot.MutableLongMap
import scala.collection.mutable.LongMap

object MutableLongMapBenchmark {
  def benchmarkVerifiedMap(n: Int, initialArraySize: Int): (Boolean, Long) = {
    val m = MutableLongMap.getEmptyLongMap(l => -1L, initialArraySize)
    // val n = Math.pow(2, 10).toInt
    val t1 = System.nanoTime()
    for (i <- 0 to n) {
      m.update(i, i)
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
      m.update(i, i)
    }
    val t2 = System.nanoTime()

    val l = for i <- 0L to n yield (m(i) == i)
    val res = l.foldLeft(true)((acc, b) => acc && b)
    (res, t2 - t1)
  }

  def benchmarkWithSeq(m: MutableLongMap.LongMap[Long] | LongMap[Long], seq: Iterable[(String, Long, Option[Long])]): Long = {
    if (m.isInstanceOf[MutableLongMap.LongMap[Long]]) {
      val mT = m.asInstanceOf[MutableLongMap.LongMap[Long]]
      val t1 = System.nanoTime()
      for ((op, k, ov) <- seq) {
        op match {
          case "update" => mT.update(k, ov.get)
          case "remove" => mT.remove(k)
        }
      }
      val t2 = System.nanoTime()
      t2 - t1
    } else if (m.isInstanceOf[LongMap[Long]]) {
      val mT = m.asInstanceOf[LongMap[Long]]
      val t1 = System.nanoTime()
      for ((op, k, ov) <- seq) {
        op match {
          case "update" => mT.update(k, ov.get)
          case "remove" => mT.remove(k)
        }
      }
      val t2 = System.nanoTime()
      t2 - t1
    } else {
      throw new Exception("Unknown type")
    }
  }
  def runSequentialBenchmark(n: Int): Unit = {
    val initialArraySize = 16
    val warmupIterationsNumber = 5

    val numberElmtStr = f"Number of elements to add: $n"
    val initArraySizeStr = f"Initial array size: $initialArraySize"
    val warmupItStr = f"Number of warmup iterations: $warmupIterationsNumber"

    println(prettyPrintInFrame(List(numberElmtStr, initArraySizeStr, warmupItStr)))

    println("Warming up verified map...")
    for (i <- 0 to warmupIterationsNumber) {
      println(f"Warmup iteration $i")
      MutableLongMapBenchmark.benchmarkVerifiedMap(n, initialArraySize)
    }
    println("Done warmup")
    println("Running benchmark for verified map...")
    val (correctVerified, timeVerified) = MutableLongMapBenchmark.benchmarkVerifiedMap(n, initialArraySize)

    println("\n----\n")

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
    val timeVerStrf = f"Time to insert $n elements in verified map: ${timeVerified.toDouble / 1000} μs = ${timeVerified.toDouble / 1000000} ms = ${timeVerified.toDouble / 1000000000} s"
    val timeOrigStr = f"Time to insert $n elements in original map: ${timeOriginal.toDouble / 1000} μs = ${timeOriginal.toDouble / 1000000} ms = ${timeOriginal.toDouble / 1000000000} s"
    val timeRatioStr = f"Ratio original/verified: ${timeOriginal.toDouble / timeVerified.toDouble}"

    println("")
    println(prettyPrintInFrame(List(timeVerStrf, timeOrigStr, timeRatioStr)))

  }

  def loadListFromFile(p: String): Iterable[(String, Long, Option[Long])] = {
    val lines = scala.io.Source.fromFile(p).getLines.toList
    val l = for (line <- lines) yield {
      val split = line.split(" ")
      val op = split(0)
      val k = split(1).toLong
      val ov = if (split.length == 3) Some(split(2).toLong) else None
      (op, k, ov)
    }
    l
  }

  def runBenchmarkWithSequences(title: String, p: String, repetitions: Int = 1): Unit = {
    val loaded: Iterable[(String, Long, Option[Long])] = loadListFromFile(p)
    val s: Iterable[(String, Long, Option[Long])] = (1 to repetitions).flatMap(_ => loaded)

    val initialArraySize = 16
    val warmupIterationsNumber = 5

    val titleStr = f"Title: $title"
    val seqFileStr = f"Sequence file: $p"
    val seqLengthStr = f"Sequence length: ${s.size}"
    val seqRepetitionsStr = f"Number of repetitions of the seq: $repetitions"
    val initArraySizeStr = f"Initial array size: $initialArraySize"
    val warmupItStr = f"Number of warmup iterations: $warmupIterationsNumber"

    println(prettyPrintInFrame(List(titleStr, seqFileStr, seqLengthStr, seqRepetitionsStr, initArraySizeStr, warmupItStr)))

    println("Warming up verified map...")
    for (i <- 0 to warmupIterationsNumber) {
      println(f"Warmup iteration $i")
      val m = MutableLongMap.getEmptyLongMap(l => -1L, initialArraySize)
      benchmarkWithSeq(m, s)
    }
    println("Done warmup")
    println("Running benchmark for verified map...")
    val mVerified = MutableLongMap.getEmptyLongMap(l => -1L, initialArraySize)
    val timeVerified = benchmarkWithSeq(mVerified, s)

    println("\n----\n")
    println("Warming up original map...")
    for (i <- 0 to warmupIterationsNumber) {
      println(f"Warmup iteration $i")
      val m = new LongMap[Long](initialArraySize)
      benchmarkWithSeq(m, s)
    }
    println("Done warmup")
    println("Running benchmark for original map...")
    val mOriginal = new LongMap[Long](initialArraySize)
    val timeOriginal = benchmarkWithSeq(mOriginal, s)

    val timeVerStrf = f"Time to insert execute sequence on verified map: ${timeVerified.toDouble / 1000} μs = ${timeVerified.toDouble / 1000000} ms = ${timeVerified.toDouble / 1000000000} s"
    val timeOrigStr = f"Time to insert execute sequence on original map: ${timeOriginal.toDouble / 1000} μs = ${timeOriginal.toDouble / 1000000} ms = ${timeOriginal.toDouble / 1000000000} s"
    val timeRatioStr = f"Ratio original/verified: ${timeOriginal.toDouble / timeVerified.toDouble}"
    println("")
    println(prettyPrintInFrame(List(timeVerStrf, timeOrigStr, timeRatioStr)))

  }

  def prettyPrintInFrame(s: List[String]): String = (List((List("|") ++ (1 to (s.map(_.size).max + 6)).map(_ => "-") ++ List("|")).mkString("")) ++ s.map(st => f"|   $st${(1 to (s.map(_.size).max - st.size)).map(_ => " ").mkString("")}   |") ++ List((List("|") ++ (1 to (s.map(_.size).max + 6)).map(_ => "-") ++ List("|")).mkString(""))).mkString("\n") + "\n\n"
}

@main def main(): Unit = {
  MutableLongMapBenchmark.runSequentialBenchmark(Math.pow(2, 10).toInt)

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences("Random 1000 update and remove operations", "./benchmark-sequences/random-1000-update-remove.txt")

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences("Update 2^^20 keys, then remove them", "./benchmark-sequences/add-2-to-20-then-remove-them.txt")

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences("Update 2^^20 keys, then remove, then update them again", "./benchmark-sequences/add-2-to-20-then-remove-then-add-again.txt")

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences(
    "Update 2^^20 keys, then remove half, then update all, randomised order",
    "./benchmark-sequences/add-2-to-20-then-remove-half-then-add-again-randomised.txt"
  )

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences(
    "Update 2^^19 keys, then remove, then update them again, randomised order",
    "./benchmark-sequences/add-2-to-19-then-remove-then-add-again-randomised.txt"
  )

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences(
    "Update 2^^19 keys, then remove, then update them again, randomised order",
    "./benchmark-sequences/add-2-to-19-then-remove-then-add-again-randomised.txt",
    10
  )

  println("\n\n\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n\n")

  MutableLongMapBenchmark.runBenchmarkWithSequences(
    "Update 2^^19 keys, then remove, then update them again, randomised order",
    "./benchmark-sequences/add-2-to-19-then-remove-then-add-again-randomised.txt",
    200
  )
}
