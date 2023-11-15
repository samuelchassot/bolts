package hashconsing
import ch.epfl.chassot.MutableLongMap

class A(val s: String, val x: Int, val y: Int, val v: Long) {
}

object A:
  private val default = new A("default", -1, -1, -1L)
  val m: MutableLongMap.LongMap[A] = MutableLongMap.getEmptyLongMap[A](k => default)

  def computeKey(s: String, x: Int, y: Int, v: Long): Long = (s.hashCode() * x.hashCode() * y.hashCode() * v.hashCode()).toLong
  def getNewA(s: String, x: Int, y: Int, v: Long): A ={
    val k = computeKey(s, x, y, v)
    val candidate = m(k)
    val found = candidate != default
    if found then
      if candidate.s == s && candidate.x == x && candidate.y == y && candidate.v == v then return candidate
    val a = new A(s, x, y, v)
    if !found then
      m.update(k, a)
    a
  } ensuring(res => res.s == s && res.x == x && res.y == y && res.v == v)

end A
