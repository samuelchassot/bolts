/** Author: Samuel Chassot
  */

package ch.epfl.chassot

import stainless.annotation._
import stainless.collection._
import stainless.equations._
import stainless.lang._
import stainless.proof.check
import scala.annotation.tailrec
import scala.collection.immutable

// Uncomment the following import to run benchmarks
// import OptimisedChecks.*

trait Ordering[T]:
  def compare(x: T, y: T): Int

  @law def inverse(x: T, y: T): Boolean =
    sign(compare(x, y)) == -sign(compare(y, x))

  @law def transitive(x: T, y: T, z: T): Boolean =
    if (compare(x, y) > 0 && compare(y, z) > 0) then compare(x, z) > 0 else true

  @law def consistent(x: T, y: T, z: T): Boolean =
    if compare(x, y) == 0 then sign(compare(x, z)) == sign(compare(y, z)) else true

  final def sign(x: Int): BigInt =
    if x < 0 then -1 else if x > 0 then 1 else 0

end Ordering

case class ListMap[K, B](toList: List[(K, B)], ordd: Ordering[K], dummy: Unit) {
  require(TupleListOps.isStrictlySorted(toList)(ordd))
  given ord: Ordering[K] = ordd

  def isEmpty(implicit ord: Ordering[K]): Boolean = toList.isEmpty

  def head(implicit ord: Ordering[K]): (K, B) = {
    require(!isEmpty)
    toList.head
  }

  def size(implicit ord: Ordering[K]): Int = {
    require(toList.size < Integer.MAX_VALUE)
    TupleListOps.intSize(toList)
  }

  @pure
  def nKeys(implicit ord: Ordering[K]): Int = {
    require(toList.size < Integer.MAX_VALUE)
    TupleListOps.intSizeKeys(TupleListOps.getKeysList(toList))
  }

  def tail(implicit ord: Ordering[K]): ListMap[K, B] = {
    require(!isEmpty)
    ListMap(toList.tail)
  }

  def contains(key: K)(implicit ord: Ordering[K]): Boolean = {
    val res = TupleListOps.containsKey(toList, key)
    if (res) {
      TupleListOps.lemmaContainsKeyImpliesGetValueByKeyDefined(toList, key)
    }
    res

  }.ensuring(res => !res || this.get(key).isDefined)

  @inline
  def get(key: K)(implicit ord: Ordering[K]): Option[B] = {
    TupleListOps.getValueByKey(toList, key)
  }

  @inline
  def keysOf(value: B)(implicit ord: Ordering[K]): List[K] = {
    TupleListOps.getKeysOf(toList, value)
  }

  def keys()(implicit ord: Ordering[K]): List[K] = {
    TupleListOps.getKeysList(toList)
  }

  def apply(key: K)(implicit ord: Ordering[K]): B = {
    require(contains(key))
    get(key).get
  }

  def +(keyValue: (K, B))(implicit ord: Ordering[K]): ListMap[K, B] = {
    
    val newList =
      TupleListOps.insertStrictlySorted(toList, keyValue._1, keyValue._2)

    TupleListOps.lemmaContainsTupThenGetReturnValue(
      newList,
      keyValue._1,
      keyValue._2
    )
    ListMap(newList)

  }.ensuring(res =>
    res.contains(keyValue._1) && res.get(keyValue._1) == Some[B](
      keyValue._2
    ) && res.toList.contains(keyValue)
  )

  def ++(keyValues: List[(K, B)])(implicit ord: Ordering[K]): ListMap[K, B] = {
    decreases(keyValues)
    keyValues match {
      case Nil()                => this
      case Cons(keyValue, rest) => (this + keyValue) ++ rest
    }
  }
  def -(key: K)(implicit ord: Ordering[K]): ListMap[K, B] = {
    ListMap(TupleListOps.removeStrictlySorted(toList, key))
  }.ensuring(res => !res.contains(key))

  def --(keys: List[K])(implicit ord: Ordering[K]): ListMap[K, B] = {
    decreases(keys)
    keys match {
      case Nil()           => this
      case Cons(key, rest) => (this - key) -- rest
    }
  }
  @inline
  def forall(p: ((K, B)) => Boolean)(implicit ord: Ordering[K]): Boolean = {
    toList.forall(p)
  }
}

object TupleListOps {

  extension [K] (k: K) def >(other: K)(implicit ord: Ordering[K]): Boolean = ord.compare(k, other) > 0
  extension [K] (k: K) def <(other: K)(implicit ord: Ordering[K]): Boolean = ord.compare(k, other) < 0
  extension [K] (k: K) def >=(other: K)(implicit ord: Ordering[K]): Boolean = ord.compare(k, other) >= 0
  extension [K] (k: K) def <=(other: K)(implicit ord: Ordering[K]): Boolean = ord.compare(k, other) <= 0

  @inline
  def invariantList[K,  B](l: List[(K, B)])(implicit ord: Ordering[K]): Boolean = {
    isStrictlySorted(l)
  }

  def getKeysList[K,  B](l: List[(K, B)])(implicit ord: Ordering[K]): List[K] = {
    require(invariantList(l))
    decreases(l)
    l match {
      case Cons(head, tl) => Cons(head._1, getKeysList(tl))
      case Nil()          => Nil[K]()
    }
  }.ensuring(res => isStrictlySortedK(res) && res.length == l.length)

  @pure
  def intSizeKeys[K](l: List[K])(implicit ord: Ordering[K]): Int = {
    require(l.length < Integer.MAX_VALUE)
    decreases(l)

    l match {
      case Cons(head, tl) => 1 + intSizeKeys(tl)
      case Nil()          => 0
    }
  }

  def intSize[K,  B](l: List[(K, B)])(implicit ord: Ordering[K]): Int = {
    decreases(l)
    l match {
      case Cons(head, tl) => {
        val s1 = intSize(tl)
        if (s1 < Integer.MAX_VALUE) {
          1 + s1
        } else {
          0
        }
      }

      case Nil() => 0
    }
  }.ensuring(res => res >= 0)

  def getKeysOf[K,  B](l: List[(K, B)], value: B)(implicit ord: Ordering[K]): List[K] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._2 == value) => {
        if (!getKeysOf(tl, value).isEmpty) {
          lemmaForallGetValueByKeySameWithASmallerHead(
            tl,
            getKeysOf(tl, value),
            value,
            head
          )

        }
        Cons(head._1, getKeysOf(tl, value))
      }
      case Cons(head, tl) if (head._2 != value) => {
        val r = getKeysOf(tl, value)
        if (!getKeysOf(tl, value).isEmpty) {
          lemmaForallGetValueByKeySameWithASmallerHead(
            tl,
            getKeysOf(tl, value),
            value,
            head
          )
        }
        getKeysOf(tl, value)
      }
      case Nil() => Nil[K]()
    }

  }.ensuring(res => res.forall(getValueByKey(l, _) == Some[B](value)))

  def filterByValue[K,  B](l: List[(K, B)], value: B)(implicit ord: Ordering[K]): List[(K, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._2 == value) =>
        head :: filterByValue(tl, value)
      case Cons(head, tl) if (head._2 != value) => filterByValue(tl, value)
      case Nil()                                => Nil[(K, B)]()
    }
  }.ensuring(res =>
    invariantList(res) && res.forall(_._2 == value) &&
      (if (l.isEmpty) res.isEmpty else res.isEmpty || res.head._1 >= l.head._1)
  )

  def getValueByKey[K,  B](l: List[(K, B)], key: K)(implicit ord: Ordering[K]): Option[B] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 == key) => Some[B](head._2)
      case Cons(head, tl) if (head._1 != key) => getValueByKey(tl, key)
      case Nil()                              => None[B]()
    }

  }

  def insertStrictlySorted[K,  B](
      l: List[(K, B)],
      newKey: K,
      newValue: B
  )(implicit ord: Ordering[K]): List[(K, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 < newKey) =>
        head :: insertStrictlySorted(tl, newKey, newValue)
      case Cons(head, tl) if (head._1 == newKey) => (newKey, newValue) :: tl
      case Cons(head, tl) if (head._1 > newKey) =>
        (newKey, newValue) :: Cons(head, tl)
      case Nil() => (newKey, newValue) :: Nil()
    }
  }.ensuring(res =>
    invariantList(res) && containsKey(res, newKey) && res.contains(
      (newKey, newValue)
    )
  )

  def removeStrictlySorted[K,  B](
      l: List[(K, B)],
      key: K
  )(implicit ord: Ordering[K]): List[(K, B)] = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 == key) => tl
      case Cons(head, tl) if (head._1 != key) =>
        head :: removeStrictlySorted(tl, key)
      case Nil() => Nil[(K, B)]()
    }
  }.ensuring(res => invariantList(res) && !containsKey(res, key))

  def isStrictlySorted[K,  B](l: List[(K, B)])(implicit ord: Ordering[K]): Boolean = {
    decreases(l)
    l match {
      case Nil()                                     => true
      case Cons(_, Nil())                            => true
      case Cons(h1, Cons(h2, _)) if (h1._1 >= h2._1) => false
      case Cons(_, t)                                => isStrictlySorted(t)
    }
  }

  def isStrictlySortedK[K](l: List[K])(implicit ord: Ordering[K]): Boolean = {
    decreases(l)
    l match {
      case Nil()                               => true
      case Cons(_, Nil())                      => true
      case Cons(h1, Cons(h2, _)) if (h1 >= h2) => false
      case Cons(_, t)                          => isStrictlySortedK(t)
    }
  }

  def containsKey[K,  B](l: List[(K, B)], key: K)(implicit ord: Ordering[K]): Boolean = {
    require(invariantList(l))
    decreases(l)
    l match {
      case Cons(head, tl) if (head._1 == key) => true
      case Cons(head, tl) if (head._1 > key)  => false
      case Cons(head, tl) if (head._1 < key)  => containsKey(tl, key)
      case Nil()                              => false

    }
  }

  // ----------- LEMMAS -----------------------------------------------------

  @opaque
  @inlineOnce
  def lemmaInsertAndRemoveStrictlySortedCommutative[K,  B](
      l: List[(K, B)],
      key1: K,
      v1: B,
      key2: K
  )(implicit ord: Ordering[K]): Unit = {
    require(key1 != key2)
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) => {
        lemmaInsertAndRemoveStrictlySortedCommutative(tl, key1, v1, key2)
      }
      case _ => ()
    }

  }.ensuring(_ =>
    insertStrictlySorted(
      removeStrictlySorted(l, key2),
      key1,
      v1
    ) == removeStrictlySorted(
      insertStrictlySorted(l, key1, v1),
      key2
    )
  )

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedThenRemoveIsSame[K,  B](
      l: List[(K, B)],
      key1: K,
      v1: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    require(!containsKey(l, key1))
    decreases(l)

    l match {
      case Cons(head, tl) => {
        lemmaInsertStrictlySortedThenRemoveIsSame(tl, key1, v1)
      }
      case _ => ()
    }

  }.ensuring(_ => removeStrictlySorted(insertStrictlySorted(l, key1, v1), key1) == l)

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedCommutative[K,  B](
      l: List[(K, B)],
      key1: K,
      v1: B,
      key2: K,
      v2: B
  )(implicit ord: Ordering[K]): Unit = {
    require(key1 != key2)
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 < key1 && head._1 < key2) => {
        lemmaInsertStrictlySortedCommutative(tl, key1, v1, key2, v2)
      }
      case _ => ()
    }

  }.ensuring(_ =>
    insertStrictlySorted(
      insertStrictlySorted(l, key1, v1),
      key2,
      v2
    ) == insertStrictlySorted(
      insertStrictlySorted(l, key2, v2),
      key1,
      v1
    )
  )

  @opaque
  @inlineOnce
  def lemmaRemoveStrictlySortedCommutative[K,  B](
      l: List[(K, B)],
      key1: K,
      key2: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) => {
        lemmaRemoveStrictlySortedCommutative(tl, key1, key2)
      }
      case _ => ()
    }

  }.ensuring(_ =>
    removeStrictlySorted(
      removeStrictlySorted(l, key1),
      key2
    ) == removeStrictlySorted(
      removeStrictlySorted(l, key2),
      key1
    )
  )

  @opaque
  @inlineOnce
  def lemmaRemoveStrictlySortedNotPresentPreserves[K,  B](
      l: List[(K, B)],
      key: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    require(!containsKey(l, key))
    decreases(l)

    l match {
      case Cons(head, tl) => {
        lemmaRemoveStrictlySortedNotPresentPreserves(tl, key)
      }
      case _ => ()
    }

  }.ensuring(_ => removeStrictlySorted(l, key) == l)

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedErasesIfSameKey[K,  B](
      l: List[(K, B)],
      key1: K,
      v1: B,
      v2: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 < key1) => {
        lemmaInsertStrictlySortedErasesIfSameKey(tl, key1, v1, v2)
      }
      case _ => ()
    }

  }.ensuring(_ =>
    insertStrictlySorted(
      insertStrictlySorted(l, key1, v1),
      key1,
      v2
    ) == insertStrictlySorted(
      l,
      key1,
      v2
    )
  )

  @opaque
  @inlineOnce
  def lemmaAddNewKeyIncrementSize[K,  B](
      l: List[(K, B)],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    require(!containsKey(l, key))
    decreases(l)

    val inserted = insertStrictlySorted(l, key, value)
    l match {
      case Cons(head, tl) if (head._1 < key) => {
        lemmaAddNewKeyIncrementSize(tl, key, value)

      }
      case Cons(head, tl) if (head._1 == key) => check(false)
      case _                                  =>
    }

  }.ensuring(_ => insertStrictlySorted(l, key, value).length == l.length + 1)

  @opaque
  @inlineOnce
  def lemmaAddExistingKeyPreservesSize[K,  B](
      l: List[(K, B)],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    decreases(l)
    require(invariantList(l))
    require(containsKey(l, key))

    val inserted = insertStrictlySorted(l, key, value)
    l match {
      case Cons(head, tl) if (head._1 < key) => {
        lemmaAddExistingKeyPreservesSize(tl, key, value)
      }
      case Cons(head, tl) if (head._1 == key) => {
        assert(inserted == Cons((key, value), tl))
      }
      case _ =>
    }

  }.ensuring(_ => insertStrictlySorted(l, key, value).length == l.length)

  @opaque
  @inlineOnce
  def lemmaGetValueByKeyIsDefinedImpliesContainsKey[K,  B](
      l: List[(K, B)],
      key: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && getValueByKey(l, key).isDefined)
    decreases(l)
    l match {
      case Cons(head, tl) if (head._1 != key) =>
        lemmaGetValueByKeyIsDefinedImpliesContainsKey(tl, key)
      case _ => ()
    }
  }.ensuring(_ => containsKey(l, key))

  @opaque
  @inlineOnce
  def lemmaContainsKeyImpliesGetValueByKeyDefined[K,  B](
      l: List[(K, B)],
      key: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && containsKey(l, key))
    decreases(l)
    l match {
      case Cons(head, tl) if (head._1 != key) =>
        lemmaContainsKeyImpliesGetValueByKeyDefined(tl, key)
      case _ => ()
    }
  }.ensuring(_ => getValueByKey(l, key).isDefined)

  @opaque
  @inlineOnce
  def lemmaForallGetValueByKeySameWithASmallerHead[K,  B](
      l: List[(K, B)],
      keys: List[K],
      value: B,
      newHead: (K, B)
  )(implicit ord: Ordering[K]): Unit = {
    require(
      invariantList(l) && !l.isEmpty &&
        keys.forall(getValueByKey(l, _) == Some[B](value)) &&
        newHead._1 < l.head._1
    )
    decreases(keys)

    keys match {
      case Cons(head, tl) => {
        lemmaGetValueByKeyIsDefinedImpliesContainsKey(l, head)
        lemmaContainsKeyImpliesGetValueByKeyDefined(Cons(newHead, l), head)
        lemmaForallGetValueByKeySameWithASmallerHead(l, tl, value, newHead)
      }
      case _ => ()
    }

  }.ensuring(_ => keys.forall(k => getValueByKey(Cons(newHead, l), k) == Some[B](value)))

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues[K,  B](
      l: List[(K, B)],
      newKey: K,
      newValue: B,
      otherKey: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && newKey != otherKey)
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 != otherKey) =>
        lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues(
          tl,
          newKey,
          newValue,
          otherKey
        )
      case _ => ()
    }

  }.ensuring(_ =>
    containsKey(
      insertStrictlySorted(l, newKey, newValue),
      otherKey
    ) == containsKey(l, otherKey) &&
      getValueByKey(
        insertStrictlySorted(l, newKey, newValue),
        otherKey
      ) == getValueByKey(
        l,
        otherKey
      )
  )

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained[K,  B](
      l: List[(K, B)],
      newKey: K,
      newValue: B,
      otherKey: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && !containsKey(l, otherKey) && otherKey != newKey)
    decreases(l)

    l match {
      case Cons(head, tl) =>
        lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained(
          tl,
          newKey,
          newValue,
          otherKey
        )
      case _ => ()
    }
  }.ensuring(_ => !containsKey(insertStrictlySorted(l, newKey, newValue), otherKey))

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained[K,  B](
      l: List[(K, B)],
      newKey: K,
      newValue: B,
      otherKey: K
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && containsKey(l, otherKey) && otherKey != newKey)
    decreases(l)

    l match {
      case Cons(head, tl) if (head._1 != otherKey) =>
        lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained(
          tl,
          newKey,
          newValue,
          otherKey
        )
      case _ => ()
    }
  }.ensuring(_ => containsKey(insertStrictlySorted(l, newKey, newValue), otherKey))

  @opaque
  @inlineOnce
  def lemmaInsertStrictlySortedNotContainedContent[K,  B](
      @induct l: List[(K, B)],
      newKey: K,
      newValue: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l))
    require(!containsKey(l, newKey))

  } ensuring (_ =>
    l.content ++ Set((newKey, newValue)) == insertStrictlySorted(
      l,
      newKey,
      newValue
    ).content
  )

  @opaque
  @inlineOnce
  def lemmaNotContainsKeyThenNotContainsTuple[K,  B](
      @induct l: List[(K, B)],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && !containsKey(l, key))

  }.ensuring(_ => !l.contains((key, value)))

  @opaque
  @inlineOnce
  def lemmaContainsTupleThenContainsKey[K,  B](
      l: List[(K, B)],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && l.contains((key, value)))
    decreases(l)

    l match {
      case Cons(head, tl) if (head != (key, value)) =>
        lemmaContainsTupleThenContainsKey(tl, key, value)
      case _ => ()
    }
  }.ensuring(_ => containsKey(l, key))

  @opaque
  @inlineOnce
  def lemmaContainsTupThenGetReturnValue[K,  B](
      l: List[(K, B)],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    require(invariantList(l) && containsKey(l, key) && l.contains((key, value)))
    decreases(l)

    l match {
      case head :: Nil() => ()
      case Cons(head, tl) if (head._1 == key) =>
        lemmaNotContainsKeyThenNotContainsTuple(tl, key, value)
      case Cons(head, tl) => lemmaContainsTupThenGetReturnValue(tl, key, value)
      case Nil()          => ()
    }
  }.ensuring(_ => getValueByKey(l, key) == Some[B](value))
}

object ListMap {
  def apply[K, B](l: List[(K, B)])(using ord: Ordering[K]): ListMap[K, B] = ListMap(l, ord, ())
  def empty[K, B](implicit ord: Ordering[K]): ListMap[K, B] = ListMap[K, B](List.empty[(K, B)])
}

object ListMapLemmas {
  import ListSpecs._

  @opaque
  @inlineOnce
  def removeNotPresentStillSame[K,  B](lm: ListMap[K, B], a: K)(implicit ord: Ordering[K]): Unit = {
    require(!lm.contains(a))
    TupleListOps.lemmaRemoveStrictlySortedNotPresentPreserves(lm.toList, a)
  }.ensuring(_ => lm - a == lm)

  @opaque
  @inlineOnce
  def addSameAsAddTwiceSameKeyDiffValues[K,  B](
      lm: ListMap[K, B],
      a: K,
      b1: B,
      b2: B
  )(implicit ord: Ordering[K]): Unit = {
    TupleListOps.lemmaInsertStrictlySortedErasesIfSameKey(lm.toList, a, b1, b2)
  }.ensuring(_ => lm + (a, b2) == (lm + (a, b1) + (a, b2)))

  @opaque
  @inlineOnce
  def addRemoveCommutativeForDiffKeys[K,  B](
      lm: ListMap[K, B],
      a1: K,
      b1: B,
      a2: K
  )(implicit ord: Ordering[K]): Unit = {
    require(a1 != a2)
    TupleListOps.lemmaInsertAndRemoveStrictlySortedCommutative(
      lm.toList,
      a1,
      b1,
      a2
    )
  }.ensuring(_ => lm + (a1, b1) - a2 == lm - a2 + (a1, b1))

  @opaque
  @inlineOnce
  def addThenRemoveForNewKeyIsSame[K,  B](
      lm: ListMap[K, B],
      a1: K,
      b1: B
  )(implicit ord: Ordering[K]): Unit = {
    require(!lm.contains(a1))
    TupleListOps.lemmaInsertStrictlySortedThenRemoveIsSame(lm.toList, a1, b1)
  }.ensuring(_ => lm + (a1, b1) - a1 == lm)

  @opaque
  @inlineOnce
  def removeCommutative[K,  B](lm: ListMap[K, B], a1: K, a2: K)(implicit ord: Ordering[K]): Unit = {
    TupleListOps.lemmaRemoveStrictlySortedCommutative(lm.toList, a1, a2)
  }.ensuring(_ => lm - a1 - a2 == lm - a2 - a1)

  @opaque
  @inlineOnce
  def addCommutativeForDiffKeys[K,  B](
      lm: ListMap[K, B],
      a1: K,
      b1: B,
      a2: K,
      b2: B
  )(implicit ord: Ordering[K]): Unit = {
    require(a1 != a2)
    TupleListOps.lemmaInsertStrictlySortedCommutative(lm.toList, a1, b1, a2, b2)
  }.ensuring(_ => lm + (a1, b1) + (a2, b2) == lm + (a2, b2) + (a1, b1))

  @opaque
  @inlineOnce
  def emptyContainsNothing[K,  B](k: K)(implicit ord: Ordering[K]): Unit = {}.ensuring(_ => !ListMap.empty[K, B].contains(k))

  @opaque
  @inlineOnce
  def addValidProp[K,  B](
      lm: ListMap[K, B],
      p: ((K, B)) => Boolean,
      a: K,
      b: B
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p) && p(a, b))
    decreases(lm.toList.size)

    if (!lm.isEmpty)
      addValidProp(lm.tail, p, a, b)

  }.ensuring { _ =>
    val nlm = lm + (a, b)
    nlm.forall(p)
  }

  @opaque
  @inlineOnce
  def removeValidProp[K,  B](
      lm: ListMap[K, B],
      p: ((K, B)) => Boolean,
      a: K
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p))
    decreases(lm.toList.size)
    if (!lm.isEmpty)
      removeValidProp(lm.tail, p, a)

  }.ensuring { _ =>
    val nlm = lm - a
    nlm.forall(p)
  }

  @opaque
  @inlineOnce
  def insertAllValidProp[K,  B](
      lm: ListMap[K, B],
      kvs: List[(K, B)],
      p: ((K, B)) => Boolean
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p) && kvs.forall(p))
    decreases(kvs)

    if (!kvs.isEmpty) {
      addValidProp(lm, p, kvs.head._1, kvs.head._2)
      insertAllValidProp(lm + kvs.head, kvs.tail, p)
    }

  }.ensuring { _ =>
    val nlm = lm ++ kvs
    nlm.forall(p)
  }

  @opaque
  @inlineOnce
  def removeAllValidProp[K,  B](
      lm: ListMap[K, B],
      l: List[K],
      p: ((K, B)) => Boolean
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p))
    decreases(l)

    if (!l.isEmpty) {
      removeValidProp(lm, p, l.head)
      removeAllValidProp(lm - l.head, l.tail, p)
    }

  }.ensuring { _ =>
    val nlm = lm -- l
    nlm.forall(p)
  }

  @opaque
  @inlineOnce
  def addApplyDifferent[K,  B](
      lm: ListMap[K, B],
      a: K,
      b: B,
      a0: K
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.contains(a0) && a0 != a)
    assert(TupleListOps.containsKey(lm.toList, a0))
    TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeyValues(
      lm.toList,
      a,
      b,
      a0
    )
    TupleListOps.lemmaContainsKeyImpliesGetValueByKeyDefined(lm.toList, a0)

  }.ensuring(_ => (lm + (a -> b)).apply(a0) == lm(a0))

  @opaque
  @inlineOnce
  def addStillContains[K,  B](
      lm: ListMap[K, B],
      a: K,
      b: B,
      a0: K
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.contains(a0))

    if (a != a0)
      TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeysContained(
        lm.toList,
        a,
        b,
        a0
      )

  }.ensuring(_ => (lm + (a, b)).contains(a0))

  @opaque
  @inlineOnce
  def addStillNotContains[K,  B](
      lm: ListMap[K, B],
      a: K,
      b: B,
      a0: K
  )(implicit ord: Ordering[K]): Unit = {
    require(!lm.contains(a0) && a != a0)

    TupleListOps.lemmaInsertStrictlySortedDoesNotModifyOtherKeysNotContained(
      lm.toList,
      a,
      b,
      a0
    )

  }.ensuring(_ => !(lm + (a, b)).contains(a0))

  @opaque
  @inlineOnce
  def applyForall[K,  B](
      lm: ListMap[K, B],
      p: ((K, B)) => Boolean,
      k: K
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p) && lm.contains(k))
    decreases(lm.toList.size)

    if (!lm.isEmpty && lm.toList.head._1 != k)
      applyForall(lm.tail, p, k)

  }.ensuring(_ => p(k, lm(k)))

  @opaque
  @inlineOnce
  def getForall[K,  B](
      lm: ListMap[K, B],
      p: ((K, B)) => Boolean,
      k: K
  )(implicit ord: Ordering[K]): Unit = {
    require(lm.forall(p))
    decreases(lm.toList.size)

    if (!lm.isEmpty && lm.toList.head._1 != k)
      getForall(lm.tail, p, k)

  }.ensuring(_ => lm.get(k).forall(v => p(k, v)))

  @opaque
  @inlineOnce
  def uniqueImage[K,  B](lm: ListMap[K, B], a: K, b: B)(implicit ord: Ordering[K]): Unit = {
    require(lm.toList.contains((a, b)))

    TupleListOps.lemmaContainsTupleThenContainsKey(lm.toList, a, b)
    TupleListOps.lemmaContainsTupThenGetReturnValue(lm.toList, a, b)

  }.ensuring(_ => lm.get(a) == Some[B](b))

  @opaque
  def keysOfSound[K,  B](@induct lm: ListMap[K, B], value: B)(implicit ord: Ordering[K]): Unit = {
    // trivial by postcondition of getKeysOf
    assert(TupleListOps.getKeysOf(lm.toList, value).forall(k => lm.get(k) == Some[B](value)))
  }.ensuring(_ => lm.keysOf(value).forall((key: K) => lm.get(key) == Some[B](value)))

  @opaque
  @inlineOnce
  def addNotContainedContent[K,  B](
      lm: ListMap[K, B],
      key: K,
      value: B
  )(implicit ord: Ordering[K]): Unit = {
    require(!lm.contains(key))
    TupleListOps.lemmaInsertStrictlySortedNotContainedContent(
      lm.toList,
      key,
      value
    )
  } ensuring (_ =>
    lm.toList.content ++ Set(
      (key, value)
    ) == (lm + (key, value)).toList.content
  )
}
