package ch.epfl.set

import stainless.annotation.*
import stainless.collection.*
import stainless.lang.{ghost => ghostExpr, *}
import stainless.proof.check

import stainless.lang.StaticChecks.* // Comment out when using the OptimisedEnsuring object below

import ch.epfl.map.MutableMapInterface.MutableMap
import ch.epfl.map.MutableHashMap
import ch.epfl.map.Hashable
import ch.epfl.map.ListMap
import ch.epfl.map.TupleListOpsGenK

object MutableHashSetCode {
  /** Helper method to create a new empty HashSet
    *
    * @param hashF: Hash function for the keys
    * @return
    */
  def getEmptyHashSet[K](hashF: Hashable[K]): MutableHashSet[K] = {
    MutableHashSet(MutableHashMap.getEmptyHashMap[K, Unit]((k: K) => (), hashF))
  }.ensuring (res => res.valid && res.size == 0)
}

final case class MutableHashSetCode[V](private val underlying: MutableMap[V, Unit]) extends MutableSetInterface.MutableSet[V]:
    override def valid: Boolean = underlying.valid

    override def size: Int = underlying.size

    override def contains(v: V): Boolean = underlying.contains(v)

    override def update(v: V): Boolean = underlying.update(v, ())

    override def remove(v: V): Boolean = underlying.remove(v)

    override def isEmpty: Boolean = underlying.isEmpty

end MutableHashSet