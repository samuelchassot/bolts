import scala.collection.mutable.LongMap
import ch.epfl.chassot.MutableLongMap

// To run it using scala-cli with enough heap space:
//   scala-cli -J -Xmx24576m BugLongMapInstrumented.scala ../MutableLongMap.scala ../StrictlyOrderedLongListMap.scala ../LongMapOriginal.scala $(find /localhome/chassot/stainless/frontends/library/stainless/ -name "*.scala")

/** This makes the LongMap hangs, when the size reaches 268 435 456 So the counter example we found in Stainless is actually triggering a bug :)
  */
object BugLongMap {

  def triggerBug() = {
    val m = new LongMap[Long](536870911 + 1)
    m.contains(0L) // just to check it is indeed the version modified with prints
    for (i <- 0 to Math.pow(2, 30).toInt) {
      m.update(i, i)
      if (i % 1000000 == 0) {
        println("Update " + i)
      }
    }
  }
  def notTriggerBugVerified(): Unit = {
    println("Test create array of empty cell for different mask")
    for (i <- 3 to 30) {
      val mask = (1 << i) - 1
      println(f"mask = $mask")
      val t1 = System.nanoTime()
      val a = Array.fill(mask + 1)(MutableLongMap.EmptyCell[Long]())
      val t2 = System.nanoTime()
      println(f"Time to create array of empty cell for mask $mask = ${(t2 - t1) / 1000000} ms")
    }

    println("Create new Map")
    val m = MutableLongMap.getEmptyLongMap(l => -1L, 536870911 + 1)

    for (i <- 0 to Math.pow(2, 30).toInt) {
      println(f"Update $i -> $i")
      m.update(i, i)
      if (i % 1000000 == 0) {
        println("Update " + i)
      }
    }
  }

}

@main def main(): Unit = {
  // BugLongMap.triggerBug()
  BugLongMap.notTriggerBugVerified()
}
