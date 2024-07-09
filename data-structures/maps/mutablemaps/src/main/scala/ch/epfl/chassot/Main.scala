package ch.epfl.chassot

import ch.epfl.chassot.MutableLongMapOpti
import stainless.annotation.*

object Main:
  @cCode.`export`
  def main(args: Array[String]): Unit = 
    def defaultValue(l: Long) = 0L
    val n = 8388608

    val m = MutableLongMapOpti.getEmptyLongMap(0L, 8388608)
    var i = 0
    while i < 4194304 do
      m.update(i, i)
      i += 1
    end while    

    i = 0

    var acc: Long = 1
    while (i < n) do
      val index = i % 4194304
      acc = acc * m(index)
      i += 1
    end while
    val temp = acc * 5
