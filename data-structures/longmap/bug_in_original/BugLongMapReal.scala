import scala.collection.mutable.LongMap
import ch.epfl.chassot.MutableLongMap

// To run it using scala-cli with enough heap space:
// scala-cli -J -Xmx24576m BugLongMapReal.scala ../MutableLongMap.scala ../StrictlyOrderedLongListMap.scala $(find /localhome/chassot/stainless/frontends/library/stainless/ -name "*.scala")

/** This makes the LongMap hangs, when the size reaches 268'435'456 So the counter example we found in Stainless is actually triggering a bug :)
  */
object BugLongMap {

  def triggerBug(): Unit = {
    val m = LongMap[Long]()
    for (i <- 0 to Math.pow(2, 30).toInt) {
      m.update(i, i)
      println(f"m.size = ${m.size} for i = $i")
    }
  }
  def notTriggerBugVerified(): Unit = {
    val m = MutableLongMap.getEmptyLongMap(l => -1L, 16)
    for (i <- 0 to Math.pow(2, 30).toInt) {
      m.update(i, i)
      println(f"m.size = ${m.size} for i = $i")
    }
  }
}

@main def main(): Unit = {
  BugLongMap.triggerBug()
  // BugLongMap.notTriggerBugVerified()
}
