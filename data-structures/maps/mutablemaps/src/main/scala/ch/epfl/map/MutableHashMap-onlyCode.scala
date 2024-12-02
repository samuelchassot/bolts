/** Author: Samuel Chassot
  */
package ch.epfl.map

import stainless.annotation._
import stainless.collection.{ListMap => ListMapStainless, ListMapLemmas => ListMapLemmasStainless, _}
import stainless.equations._
import stainless.lang.{ghost => ghostExpr, _}
import stainless.proof.check
import scala.annotation.tailrec
import stainless.lang.Cell
import MutableLongMap._
import LongMapFixedSize.validMask

import stainless.lang.StaticChecks.* // Comment out when using the OptimisedEnsuring object below
// import OptimisedChecks.* // Import to remove `ensuring` and `require` from the code for the benchmarks

import MutableMapInterface.MutableMap

object MutableHashMapCode {

  /** Helper method to create a new empty HashMap
    *
    * @param defaultValue
    * @return
    */
  def getEmptyHashMap[K, V](defaultValue: K => V, hashF: Hashable[K]): HashMap[K, V] = {
    val initialSize = 16
    HashMap(Cell(MutableLongMap.getEmptyLongMap[List[(K, V)]]((l: Long) => Nil[(K, V)](), initialSize)), hashF, 0, defaultValue)
  }.ensuring (res => res.valid && res.size == 0)


  @mutable
  final case class HashMapCode[K, V](
      val underlying: Cell[LongMap[List[(K, V)]]],
      val hashF: Hashable[K],
      var _size: Int,
      val defaultValue: K => V
  ) extends MutableMap[K, V] {

    override def defaultEntry: K => V = this.defaultValue

    override def abstractMap: ListMap[K, V] = {
      require(valid)
      this.map
    }

    def imbalanced(): Boolean = underlying.v.imbalanced()

    override def size: Int = _size

    override def isEmpty: Boolean = underlying.v.isEmpty

    override def contains(key: K): Boolean = {
      val hash = hashF.hash(key)
      underlying.v.contains(hash) && getPair(underlying.v.apply(hash), key).isDefined
    }

    override def apply(key: K): V = {
      if (!contains(key)) {
        defaultValue(key)
      } else {
        val hash = hashF.hash(key)
        getPair(underlying.v.apply(hash), key).get._2
      }
    }

    override def update(key: K, v: V): Boolean = {
      val contained = contains(key)
      val res = if (contained) {
        val hash = hashF.hash(key)
        val currentBucket = underlying.v.apply(hash)
        // currentBucket contains the key and it is defined
        val newBucket = Cons((key, v), removePairForKey(currentBucket, key))
        val res = underlying.v.update(hash, newBucket)
        if (res && !contained) then _size += 1
        res
      } else {
        val hash = hashF.hash(key)
        val currentBucket = if underlying.v.contains(hash) then underlying.v.apply(hash) else Nil[(K, V)]()
        // Either currentBucket is empty, or it does not contain the key
        val newBucket = Cons((key, v), currentBucket)
        val res = underlying.v.update(hash, newBucket)
        if (res && !contained) then _size += 1
        res
      }
      res
    }

    override def remove(key: K): Boolean = {
      val contained = contains(key)
      if (!contained) {
        true
      } else {
        val hash = hashF.hash(key)
        val currentBucket = underlying.v.apply(hash)
        val newBucket = removePairForKey(currentBucket, key)
        val res = underlying.v.update(hash, newBucket)
        if (res && contained) then _size -= 1
        res
      }
    }

    override  def valid: Boolean = underlying.v.valid &&
      underlying.v.map.toList.forall((k, v) => noDuplicateKeys(v)) &&
      allKeysSameHashInMap(underlying.v.map, hashF)
  }
  def getPair[K, V](l: List[(K, V)], key: K): Option[(K, V)] = {
    require(noDuplicateKeys(l))
    l match
      case Cons(hd, tl) if hd._1 == key => Some(hd)
      case Cons(_, tl)                  => getPair(tl, key)
      case Nil()                        => None()
  }.ensuring (res => res.isEmpty && !containsKey(l, key) || res.isDefined && res.get._1 == key && l.contains(res.get))

  def removePairForKey[K, V](l: List[(K, V)], key: K): List[(K, V)] = {
    require(noDuplicateKeys(l))
    l match
      case Cons(hd, tl) if hd._1 == key => tl
      case Cons(hd, tl)                 => Cons(hd, removePairForKey(tl, key))
      case Nil()                        => Nil()
  }.ensuring (res => !containsKey(res, key))

}

