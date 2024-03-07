/** Author: Samuel Chassot
  */

package ch.epfl.lexer

import stainless.equations._
import stainless.lang.{ghost => ghostExpr, *}
import stainless.collection._
import stainless.annotation._
import stainless.proof._
import ch.epfl.chassot.MutableLongMap._
import ch.epfl.chassot.ListLongMap
import stainless.lang.StaticChecks._

trait IDGiver[C] {
  def id(c: C): Long
  val MAX_ID = Int.MaxValue
  @law def smallEnough(c: C): Boolean = id(c) >= 0 && id(c) <= MAX_ID
  @law def uniqueness(c1: C, c2: C): Boolean = if (id(c1) == id(c2)) then c1 == c2 else true
}

object Memoisation {
  import VerifiedRegex._
  import VerifiedRegexMatcher._

  @ghost
  @pure
  def validIdToDerivatives[C](_idToRegexes: ListLongMap[List[Regex[C]]], _idToDerivatives: ListLongMap[List[(C, Regex[C])]], id: Long): Boolean = {
    // require(_idToRegexes.contains(id))
    // require(_idToDerivatives.contains(id))
    if(!_idToRegexes.contains(id) || !_idToDerivatives.contains(id)){
      false
    } else {
      _idToRegexes.apply(id) match {
        case Nil()           => false
        case Cons(hd, Nil()) => _idToDerivatives.apply(id).forall((c, reg) => validRegex(hd) && derivativeStep(hd, c) == reg)
        case Cons(hd, tl)    => true
      }
    }
  }

  final case class Cache[C](@ghost val ids: List[Long], idToRegexes: LongMap[List[Regex[C]]], idToDerivatives: LongMap[List[(C, Regex[C])]]) {
    require(idToRegexes.valid)
    require(idToDerivatives.valid)

    @ghost
    @pure
    def validContains: Boolean =
      val idToRegMap = idToRegexes.map
      ids.forall(i => idToRegMap.contains(i) && !idToRegMap.apply(i).isEmpty) && ids.forall(idToDerivatives.map.contains)
    @ghost
    @pure
    def validContent: Boolean =
      val idToRegMap = idToRegexes.map
      val idToDerivMap = idToDerivatives.map
      ids.forall(id => validIdToDerivatives(idToRegMap, idToDerivMap, id))

    @ghost
    @pure
    def valid: Boolean = validContains && validContent

    private def getDerivativeFromList(l: List[(C, Regex[C])], a: C): Option[Regex[C]] = l match
      case Cons(h, t) if h._1 == a => Some(h._2)
      case Cons(_, t)              => getDerivativeFromList(t, a)
      case Nil()                   => None()

    /** Checks whether the regex is in the cache Meaning that it has been cached and no collision happened
      *
      * @param r
      */
    @pure
    def cacheContains(r: Regex[C])(implicit idGiver: IDGiver[C]): Boolean = {
      require(validRegex(r))
      require(valid)
      val id = hashId(r)
      if (idToRegexes.contains(id)) {
        idToRegexes.apply(id) match {
          case Cons(hd, Nil()) =>
            // Good case: the regex is in the ids list, and no collision happened
            hd == r
          case Cons(hd, tl) =>
            // Collision happened, i.e., 2 different regexes got the same id, abort
            false
          case Nil() =>
            // The regex was never cached
            false
        }
      } else {
        // The regex was never cached
        ghostExpr(if (ids.contains(id)) {
          assert(validContent)
          val idToRegMap = idToRegexes.map
          ListSpecs.forallContained(ids, i => idToRegMap.contains(i) && !idToRegMap.apply(i).isEmpty, id)
          assert(idToRegexes.map.contains(id))

        })
        assert(!ids.contains(id))
        false
      }
    } ensuring(res => !res || ids.contains(hashId(r)))

    @pure
    def getDerivative(r: Regex[C], a: C)(implicit idGiver: IDGiver[C]): Option[Regex[C]] = {
      require(validRegex(r))
      require(valid)
      require(cacheContains(r))
      val id = hashId(r)
      val res = getDerivativeFromList(idToDerivatives.apply(id), a)
      assert(valid)
      if(res.isEmpty){
        ghostExpr({check(valid && (res.isEmpty || res.get == derivativeStep(r, a)))})
      } else{
        assert(valid)
        ghostExpr({
          assert(cacheContains(r))
          assert(validContent)
          val idToRegMap = idToRegexes.map
          val idToDerivMap = idToDerivatives.map
          ListSpecs.forallContained(ids, id => idToDerivMap.contains(id), id)
          ListSpecs.forallContained(ids, i => idToRegMap.contains(i) && !idToRegMap.apply(i).isEmpty, id)

          assert(idToRegMap.contains(id))
          assert(idToDerivMap.contains(id))
          assert(idToRegexes.contains(id))
          assert(ids.forall(id => validIdToDerivatives(idToRegMap, idToDerivMap, id)))
          ListSpecs.forallContained(ids, id => validIdToDerivatives(idToRegMap, idToDerivMap, id), id)
          assert(ids.contains(id))
          assert(valid)
          check(validIdToDerivatives(idToRegexes.map, idToDerivatives.map, id))
           idToRegexes.apply(id) match {
            case Nil()           => check(false)
            case Cons(hd, Nil()) => 
              check(idToDerivMap.apply(id).forall((c, reg) => validRegex(hd) && derivativeStep(hd, c) == reg))
              check(r == hd)
              assert(r == hd)
              check(idToDerivMap.apply(id).forall((c, reg) => validRegex(r) && derivativeStep(r, c) == reg))
            case Cons(hd, tl)    => 
              // cacheContains(r) is false in this case
              check(false)
          }
          check(idToDerivMap.apply(id).forall((c, reg) => validRegex(r) && derivativeStep(r, c) == reg))
          ListSpecs.forallContained(idToDerivMap.apply(id), (c, reg) => validRegex(r) && derivativeStep(r, c) == reg, (a, res.get))
        })
        assert(res.get == derivativeStep(r, a))
        ghostExpr({check(valid && (res.isEmpty || res.get == derivativeStep(r, a)))})
      }
      res

    } ensuring (res => valid && (res.isEmpty || res.get == derivativeStep(r, a)))
  }
}
object VerifiedRegex {
  abstract sealed class Regex[C] {}

  def validRegex[C](r: Regex[C]): Boolean = r match {
    case ElementMatch(c)    => true
    case Star(r)            => !nullable(r) && !isEmptyLang(r) && validRegex(r)
    case Union(rOne, rTwo)  => validRegex(rOne) && validRegex(rTwo)
    case Concat(rOne, rTwo) => validRegex(rOne) && validRegex(rTwo)
    case EmptyExpr()        => true
    case EmptyLang()        => true
  }

  def regexDepth[C](r: Regex[C]): BigInt = {
    decreases(r)
    r match {
      case ElementMatch(c)    => BigInt(1)
      case Star(r)            => BigInt(1) + regexDepth(r)
      case Union(rOne, rTwo)  => BigInt(1) + Utils.maxBigInt(regexDepth(rOne), regexDepth(rTwo))
      case Concat(rOne, rTwo) => BigInt(1) + Utils.maxBigInt(regexDepth(rOne), regexDepth(rTwo))
      case EmptyExpr()        => BigInt(1)
      case EmptyLang()        => BigInt(1)
    }
  } ensuring (res =>
    res > 0 && (r match {
      case Union(rOne, rTwo)  => res > regexDepth(rOne) && res > regexDepth(rTwo)
      case Concat(rOne, rTwo) => res > regexDepth(rOne) && res > regexDepth(rTwo)
      case Star(r)            => res > regexDepth(r)
      case _                  => res == BigInt(1)
    })
  )

  def hashId[C](r: Regex[C])(implicit idC: IDGiver[C]): Long = {
    decreases(r)
    r match {
      case ElementMatch(c) =>
        2L * idC.id(c)
      case Star(r) =>
        3L * hashId(r)
      case Union(rOne, rTwo) =>
        5L * hashId(rOne) + 5L * hashId(rTwo)
      case Concat(rOne, rTwo) =>
        7L * hashId(rOne) + 7L * hashId(rTwo)
      case EmptyExpr() =>
        11L
      case EmptyLang() =>
        13L
    }
  }

  case class ElementMatch[C](c: C) extends Regex[C]
  case class Star[C](reg: Regex[C]) extends Regex[C]
  case class Union[C](regOne: Regex[C], regTwo: Regex[C]) extends Regex[C]
  case class Concat[C](regOne: Regex[C], regTwo: Regex[C]) extends Regex[C]

  /** Regex that accepts only the empty string: represents the language {""}
    */
  case class EmptyExpr[C]() extends Regex[C]

  /** Regex that accepts nothing: represents the empty language
    */
  case class EmptyLang[C]() extends Regex[C]

  def usedCharacters[C](r: Regex[C]): List[C] = {
    r match {
      case EmptyExpr()        => Nil[C]()
      case EmptyLang()        => Nil[C]()
      case ElementMatch(c)    => List(c)
      case Star(r)            => usedCharacters(r)
      case Union(rOne, rTwo)  => usedCharacters(rOne) ++ usedCharacters(rTwo)
      case Concat(rOne, rTwo) => usedCharacters(rOne) ++ usedCharacters(rTwo)
    }
  }

  def firstChars[C](r: Regex[C]): List[C] = {
    r match {
      case EmptyExpr()                           => Nil[C]()
      case EmptyLang()                           => Nil[C]()
      case ElementMatch(c)                       => List(c)
      case Star(r)                               => firstChars(r)
      case Union(rOne, rTwo)                     => firstChars(rOne) ++ firstChars(rTwo)
      case Concat(rOne, rTwo) if nullable(rOne)  => firstChars(rOne) ++ firstChars(rTwo)
      case Concat(rOne, rTwo) if !nullable(rOne) => firstChars(rOne)
    }
  }

  def nullable[C](r: Regex[C]): Boolean = {
    r match {
      case EmptyExpr()        => true
      case EmptyLang()        => false
      case ElementMatch(c)    => false
      case Star(r)            => true
      case Union(rOne, rTwo)  => nullable(rOne) || nullable(rTwo)
      case Concat(rOne, rTwo) => nullable(rOne) && nullable(rTwo)
    }
  }

  @inline
  def isEmptyExpr[C](r: Regex[C]): Boolean = {
    r match {
      case EmptyExpr() => true
      case _           => false
    }
  }
  @inline
  def isEmptyLang[C](r: Regex[C]): Boolean = {
    r match {
      case EmptyLang() => true
      case _           => false
    }
  }
  @inline
  def isElementMatch[C](r: Regex[C]): Boolean = {
    r match {
      case ElementMatch(_) => true
      case _               => false
    }
  }
  @inline
  def elementMatchIsChar[C](r: Regex[C], c: C): Boolean = {
    require(isElementMatch(r))
    r match {
      case ElementMatch(cc) => c == cc
    }
  }
  @inline
  def isStar[C](r: Regex[C]): Boolean = {
    r match {
      case Star(_) => true
      case _       => false
    }
  }
  @inline
  def isUnion[C](r: Regex[C]): Boolean = {
    r match {
      case Union(_, _) => true
      case _           => false
    }
  }
  @inline
  def unionInnersEquals[C](r: Regex[C], r1: Regex[C], r2: Regex[C]): Boolean = {
    require(isUnion(r))
    r match {
      case Union(rOne, rTwo) => r1 == rOne && r2 == rTwo
    }
  }

  @inline
  def isConcat[C](r: Regex[C]): Boolean = {
    r match {
      case Concat(_, _) => true
      case _            => false
    }
  }
}

object VerifiedRegexMatcher {
  import VerifiedRegex._
  import ListUtils._
  import Memoisation._

  def derivativeStep[C](r: Regex[C], a: C): Regex[C] = {
    require(validRegex(r))
    decreases(r)
    val res: Regex[C] = r match {
      case EmptyExpr()       => EmptyLang()
      case EmptyLang()       => EmptyLang()
      case ElementMatch(c)   => if (a == c) EmptyExpr() else EmptyLang()
      case Union(rOne, rTwo) => Union(derivativeStep(rOne, a), derivativeStep(rTwo, a))
      case Star(rInner)      => Concat(derivativeStep(rInner, a), Star(rInner))
      case Concat(rOne, rTwo) => {
        if (nullable(rOne)) Union(Concat(derivativeStep(rOne, a), rTwo), derivativeStep(rTwo, a))
        else Union(Concat(derivativeStep(rOne, a), rTwo), EmptyLang())
      }
    }
    res
  } ensuring (res => validRegex(res))

  // def derivativeStepMem[C](r: Regex[C], a: C)(cache: Cache[C]): Regex[C] = {
  //   require(validRegex(r))
  //   require(cache.valid)
  //   decreases(r)
  //   val id = hashId(r)

  //   res
  // } ensuring (res => res == derivativeStep(r, a))

  def derivative[C](r: Regex[C], input: List[C]): Regex[C] = {
    require(validRegex(r))
    input match {
      case Cons(hd, tl) => derivative(derivativeStep(r, hd), tl)
      case Nil()        => r
    }
  } ensuring (res => validRegex(res))

  def matchR[C](r: Regex[C], input: List[C]): Boolean = {
    require(validRegex(r))
    decreases(input.size)
    if (input.isEmpty) nullable(r) else matchR(derivativeStep(r, input.head), input.tail)
  } ensuring (res =>
    r match {
      case EmptyExpr() => res == input.isEmpty
      case EmptyLang() => !res
      case ElementMatch(c) =>
        (res && !input.isEmpty && input.tail.isEmpty && input.head == c) || (!res && !(!input.isEmpty && input.tail.isEmpty && input.head == c))
      case _ => true
    }
  )

  def matchRSpec[C](r: Regex[C], s: List[C]): Boolean = {
    require(validRegex(r))
    decreases(s.size + regexDepth(r))
    r match {
      case EmptyExpr()     => s.isEmpty
      case EmptyLang()     => false
      case ElementMatch(c) => s == List(c)
      case Union(r1, r2)   => matchRSpec(r1, s) || matchRSpec(r2, s)
      case Star(rInner)    => s.isEmpty || findConcatSeparation(rInner, Star(rInner), Nil(), s, s).isDefined
      case Concat(r1, r2)  => findConcatSeparation(r1, r2, Nil(), s, s).isDefined
    }
  }

  def mainMatchTheorem[C](r: Regex[C], s: List[C]): Unit = {
    require(validRegex(r))
    decreases(s.size + regexDepth(r))
    r match {
      case EmptyExpr()     => ()
      case EmptyLang()     => ()
      case ElementMatch(c) => ()
      case Union(r1, r2) => {
        if (matchR(r, s)) {
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(r1, r2, s)
          mainMatchTheorem(r1, s)
          mainMatchTheorem(r2, s)
        } else {
          if (matchR(r1, s)) {
            lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(r1, r2, s)
            check(false)
          }
          if (matchR(r2, s)) {
            lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(r2, r1, s)
            lemmaReversedUnionAcceptsSameString(r2, r1, s)
            check(false)
          }
          mainMatchTheorem(r1, s)
          mainMatchTheorem(r2, s)
        }

      }
      case Star(rInner) => {
        if (s.isEmpty) {
          ()
        } else {
          val cut = findConcatSeparation(rInner, Star(rInner), Nil(), s, s)
          if (cut.isDefined) {
            mainMatchTheorem(rInner, cut.get._1)
            mainMatchTheorem(Star(rInner), cut.get._2)
            if (!matchR(r, s)) {
              lemmaFindSeparationIsDefinedThenConcatMatches(rInner, Star(rInner), cut.get._1, cut.get._2, s)
              check(false)
            }
          } else {
            if (matchR(r, s)) {
              lemmaStarAppConcat(rInner, s)
              lemmaConcatAcceptsStringThenFindSeparationIsDefined(rInner, Star(rInner), s)
              check(false)
            }
          }
        }
      }
      case Concat(r1, r2) => {
        if (matchR(r, s)) {
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(r1, r2, s)
        } else {
          val cut = findConcatSeparation(r1, r2, Nil(), s, s)
          if (cut.isDefined) {
            lemmaFindSeparationIsDefinedThenConcatMatches(r1, r2, cut.get._1, cut.get._2, s)
            check(false)
          }
        }

      }
    }
  } ensuring (_ => matchR(r, s) == matchRSpec(r, s))

  /** Enumerate all cuts in s and returns one that works, i.e., r1 matches s1 and r2 matches s2 Specifically, it is the right most one, i.e., s2 is the largest, if multiple exists Returns None is no valid cut
    * exists
    *
    * @param r1
    * @param r2
    * @param s1
    * @param s2
    * @param s
    */
  def findConcatSeparation[C](r1: Regex[C], r2: Regex[C], s1: List[C], s2: List[C], s: List[C]): Option[(List[C], List[C])] = {
    require(validRegex(r1))
    require(validRegex(r2))
    require(s1 ++ s2 == s)
    decreases(s2.size)

    val res: Option[(List[C], List[C])] = (s1, s2) match {
      case (_, _) if matchR(r1, s1) && matchR(r2, s2) => Some((s1, s2))
      case (_, Nil())                                 => None()
      case (_, Cons(hd2, tl2)) => {
        lemmaMoveElementToOtherListKeepsConcatEq(s1, hd2, tl2, s)
        assert(s1 ++ List(hd2) ++ tl2 == s)
        findConcatSeparation(r1, r2, s1 ++ List(hd2), tl2, s)
      }
    }
    res

  } ensuring (res => (res.isDefined && matchR(r1, res.get._1) && matchR(r2, res.get._2) && res.get._1 ++ res.get._2 == s) || !res.isDefined)

  def findLongestMatch[C](r: Regex[C], input: List[C]): (List[C], List[C]) = {
    require(validRegex(r))
    findLongestMatchInner(r, Nil(), input)
  } ensuring (res => res._1 ++ res._2 == input)

  def findLongestMatchInner[C](r: Regex[C], testedP: List[C], totalInput: List[C]): (List[C], List[C]) = {
    require(validRegex(r))
    require(ListUtils.isPrefix(testedP, totalInput))
    decreases(totalInput.size - testedP.size)

    if (testedP == totalInput) {
      if (nullable(r)) {
        (testedP, Nil[C]())
      } else {
        (Nil[C](), totalInput)
      }
    } else {
      ListUtils.lemmaIsPrefixThenSmallerEqSize(testedP, totalInput)
      if (testedP.size == totalInput.size) {
        ListUtils.lemmaIsPrefixRefl(totalInput, totalInput)
        ListUtils.lemmaIsPrefixSameLengthThenSameList(totalInput, testedP, totalInput)
        check(false)
      }
      assert(testedP.size < totalInput.size)
      val suffix = ListUtils.getSuffix(totalInput, testedP)
      val newP = testedP ++ List(suffix.head)
      lemmaAddHeadSuffixToPrefixStillPrefix(testedP, totalInput)
      if (nullable(r)) {
        val recursive = findLongestMatchInner(derivativeStep(r, suffix.head), newP, totalInput)
        if (recursive._1.isEmpty) {
          (testedP, ListUtils.getSuffix(totalInput, testedP))
        } else {
          recursive
        }
      } else {
        findLongestMatchInner(derivativeStep(r, suffix.head), newP, totalInput)
      }
    }
  } ensuring (res => res._1 ++ res._2 == totalInput && (res._1.isEmpty || res._1.size >= testedP.size))

  // Longest match theorems
  def longestMatchIsAcceptedByMatchOrIsEmpty[C](r: Regex[C], input: List[C]): Unit = {
    require(validRegex(r))
    longestMatchIsAcceptedByMatchOrIsEmptyRec(r, r, Nil(), input)

  } ensuring (_ => findLongestMatchInner(r, Nil(), input)._1.isEmpty || matchR(r, findLongestMatchInner(r, Nil(), input)._1))

  def longestMatchNoBiggerStringMatch[C](baseR: Regex[C], input: List[C], returnP: List[C], bigger: List[C]): Unit = {
    require(validRegex(baseR))
    require(ListUtils.isPrefix(returnP, input))
    require(ListUtils.isPrefix(bigger, input))
    require(bigger.size >= returnP.size)
    require(findLongestMatchInner(baseR, Nil(), input)._1 == returnP)

    if (bigger.size == returnP.size) {
      ListUtils.lemmaIsPrefixSameLengthThenSameList(bigger, returnP, input)
    } else {
      if (matchR(baseR, bigger)) {
        lemmaKnownAcceptedStringThenFromSmallPAtLeastThat(baseR, baseR, input, Nil(), bigger)
        check(false)
      }
    }

  } ensuring (_ => bigger == returnP || !matchR(baseR, bigger))

  def lemmaIfMatchRThenLongestMatchFromThereReturnsAtLeastThis[C](baseR: Regex[C], r: Regex[C], input: List[C], testedP: List[C]): Unit = {
    require(validRegex(baseR))
    require(validRegex(r))
    require(ListUtils.isPrefix(testedP, input))
    require(matchR(baseR, testedP))
    require(derivative(baseR, testedP) == r)

    lemmaMatchRIsSameAsWholeDerivativeAndNil(baseR, testedP)
    assert(matchR(r, Nil()))
    assert(nullable(r))

  } ensuring (_ => findLongestMatchInner(r, testedP, input)._1.size >= testedP.size)

  def lemmaKnownAcceptedStringThenFromSmallPAtLeastThat[C](baseR: Regex[C], r: Regex[C], input: List[C], testedP: List[C], knownP: List[C]): Unit = {
    require(validRegex(baseR))
    require(validRegex(r))
    require(ListUtils.isPrefix(testedP, input))
    require(ListUtils.isPrefix(knownP, input))
    require(knownP.size >= testedP.size)
    require(matchR(baseR, knownP))
    require(derivative(baseR, testedP) == r)
    decreases(knownP.size - testedP.size)

    if (testedP.size == knownP.size) {
      ListUtils.lemmaIsPrefixSameLengthThenSameList(testedP, knownP, input)
      lemmaIfMatchRThenLongestMatchFromThereReturnsAtLeastThis(baseR, r, input, testedP)
      check(findLongestMatchInner(r, testedP, input)._1.size >= knownP.size)
    } else {
      assert(testedP.size < input.size)
      val suffix = ListUtils.getSuffix(input, testedP)
      val newP = testedP ++ List(suffix.head)
      lemmaAddHeadSuffixToPrefixStillPrefix(testedP, input)

      lemmaDerivativeOnLWithANewCharIsANewDerivativeStep(baseR, r, testedP, suffix.head)
      lemmaKnownAcceptedStringThenFromSmallPAtLeastThat(baseR, derivativeStep(r, suffix.head), input, newP, knownP)

      check(findLongestMatchInner(r, testedP, input)._1.size >= knownP.size)
    }

  } ensuring (_ => findLongestMatchInner(r, testedP, input)._1.size >= knownP.size)

  def longestMatchIsAcceptedByMatchOrIsEmptyRec[C](baseR: Regex[C], r: Regex[C], testedP: List[C], input: List[C]): Unit = {
    require(validRegex(baseR))
    require(ListUtils.isPrefix(testedP, input))
    require(derivative(baseR, testedP) == r)
    decreases(input.size - testedP.size)

    if (findLongestMatchInner(r, testedP, input)._1.isEmpty) {
      ()
    } else {
      if (testedP == input) {
        if (nullable(r)) {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(baseR, testedP)
        } else {
          ()
        }
      } else {
        ListUtils.lemmaIsPrefixThenSmallerEqSize(testedP, input)
        if (testedP.size == input.size) {
          ListUtils.lemmaIsPrefixRefl(input, input)
          ListUtils.lemmaIsPrefixSameLengthThenSameList(input, testedP, input)
          check(false)
        }
        assert(testedP.size < input.size)
        val suffix = ListUtils.getSuffix(input, testedP)
        val newP = testedP ++ List(suffix.head)
        lemmaAddHeadSuffixToPrefixStillPrefix(testedP, input)
        if (nullable(r)) {
          val recursive = findLongestMatchInner(derivativeStep(r, suffix.head), newP, input)
          if (recursive._1.isEmpty) {
            lemmaMatchRIsSameAsWholeDerivativeAndNil(baseR, testedP)
          } else {
            lemmaDerivativeOnLWithANewCharIsANewDerivativeStep(baseR, r, testedP, suffix.head)
            longestMatchIsAcceptedByMatchOrIsEmptyRec(baseR, derivativeStep(r, suffix.head), newP, input)
          }
        } else {
          lemmaDerivativeOnLWithANewCharIsANewDerivativeStep(baseR, r, testedP, suffix.head)
          longestMatchIsAcceptedByMatchOrIsEmptyRec(baseR, derivativeStep(r, suffix.head), newP, input)
        }
      }
    }

  } ensuring (_ => findLongestMatchInner(r, testedP, input)._1.isEmpty || matchR(baseR, findLongestMatchInner(r, testedP, input)._1))

  def lemmaMatchRIsSameAsWholeDerivativeAndNil[C](r: Regex[C], input: List[C]): Unit = {
    require(validRegex(r))
    input match {
      case Cons(hd, tl) => lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(r, hd), tl)
      case Nil()        => ()
    }
  } ensuring (_ => matchR(r, input) == matchR(derivative(r, input), Nil()))

  def lemmaDerivativeOnLWithANewCharIsANewDerivativeStep[C](baseR: Regex[C], r: Regex[C], input: List[C], c: C): Unit = {
    require(validRegex(baseR))
    require(derivative(baseR, input) == r)

    input match {
      case Cons(hd, tl) => lemmaDerivativeOnLWithANewCharIsANewDerivativeStep(derivativeStep(baseR, hd), r, tl, c)
      case Nil()        => ()
    }

  } ensuring (_ => derivative(baseR, input ++ List(c)) == derivativeStep(r, c))

  // Basic lemmas
  def lemmaIfAcceptEmptyStringThenNullable[C](r: Regex[C], s: List[C]): Unit = {
    require(validRegex(r))
    require(s.isEmpty)
    require(matchR(r, s))
  } ensuring (_ => nullable(r))

  def lemmaRegexAcceptsStringThenDerivativeAcceptsTail[C](r: Regex[C], s: List[C]): Unit = {
    require(validRegex(r))
    require(matchR(r, s))

  } ensuring (_ => if (s.isEmpty) nullable(r) else matchR(derivativeStep(r, s.head), s.tail))

  // EmptyString Lemma
  def lemmaRegexEmptyStringAcceptsTheEmptyString[C](r: EmptyExpr[C]): Unit = {
    require(validRegex(r))
  } ensuring (_ => matchR(r, List()))

  // Single Character Lemma
  def lemmaElementRegexAcceptsItsCharacterAndOnlyIt[C](
      r: ElementMatch[C],
      c: C,
      d: C
  ): Unit = {
    require(validRegex(r) && r == ElementMatch(c))
    require(c != d)
  } ensuring (_ => matchR(r, List(c)) && !matchR(r, List(d)))

  def lemmaElementRegexDoesNotAcceptMultipleCharactersString[C](
      r: ElementMatch[C],
      c: C,
      s: List[C]
  ): Unit = {
    require(validRegex(r) && r == ElementMatch(c))
    require(!s.isEmpty)
  } ensuring (_ => !matchR(r, Cons(c, s)))

  // Union lemmas
  def lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo[C](
      r1: Regex[C],
      r2: Regex[C],
      s: List[C]
  ): Unit = {
    require(validRegex(r1) && validRegex(r2))
    require(matchR(r1, s))

    s match {
      case Cons(hd, tl) => {
        lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(derivativeStep(r1, hd), derivativeStep(r2, hd), tl)
        assert(matchR(Union(r1, r2), s))
      }
      case Nil() => assert(matchR(Union(r1, r2), s))
    }
  } ensuring (_ => matchR(Union(r1, r2), s))

  def lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts[C](r1: Regex[C], r2: Regex[C], s: List[C]): Unit = {
    require(validRegex(r1) && validRegex(r2))
    require(matchR(Union(r1, r2), s))

    s match {
      case Cons(hd, tl) => {
        lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(derivativeStep(r1, hd), derivativeStep(r2, hd), tl)
      }
      case Nil() =>
    }
  } ensuring (_ => matchR(r1, s) || matchR(r2, s))

  def lemmaReversedUnionAcceptsSameString[C](
      r1: Regex[C],
      r2: Regex[C],
      s: List[C]
  ): Unit = {
    require(validRegex(r1) && validRegex(r2))
    require(matchR(Union(r1, r2), s))

    s match {
      case Cons(hd, tl) => {
        lemmaReversedUnionAcceptsSameString(derivativeStep(r1, hd), derivativeStep(r2, hd), tl)
        assert(matchR(Union(r2, r1), s))
      }
      case Nil() => assert(matchR(Union(r1, r2), s))
    }
  } ensuring (_ => matchR(Union(r2, r1), s))

  // Concat lemmas

  def lemmaRegexConcatWithNullableAcceptsSameStr[C](
      r1: Regex[C],
      r2: Regex[C],
      s: List[C]
  ): Unit = {
    require(validRegex(r1))
    require(validRegex(r2))
    require(matchR(r1, s))
    require(nullable(r2))

    val newR = Concat(r2, r1)

    s match {
      case Cons(hd, tl) => {
        assert(nullable(r2))
        assert(
          derivativeStep(newR, hd) == Union(Concat(derivativeStep(r2, hd), r1), derivativeStep(r1, hd))
        )
        lemmaRegexAcceptsStringThenDerivativeAcceptsTail(r1, s)
        lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(
          derivativeStep(r1, hd),
          Concat(derivativeStep(r2, hd), r1),
          tl
        )
        lemmaReversedUnionAcceptsSameString(derivativeStep(r1, hd), Concat(derivativeStep(r2, hd), r1), tl)
      }
      case Nil() => ()
    }
  } ensuring (_ => matchR(Concat(r2, r1), s))

  def lemmaTwoRegexMatchThenConcatMatchesConcatString[C](
      r1: Regex[C],
      r2: Regex[C],
      s1: List[C],
      s2: List[C]
  ): Unit = {
    require(validRegex(r1) && validRegex(r2))
    require(matchR(r1, s1))
    require(matchR(r2, s2))
    decreases(s1)

    s1 match {
      case Cons(hd, tl) => {
        lemmaTwoRegexMatchThenConcatMatchesConcatString(derivativeStep(r1, hd), r2, tl, s2)
        assert(matchR(Concat(derivativeStep(r1, hd), r2), tl ++ s2))
        if (nullable(r1)) {
          assert(
            derivativeStep(Concat(r1, r2), hd) == Union(Concat(derivativeStep(r1, hd), r2), derivativeStep(r2, hd))
          )
          lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(
            Concat(derivativeStep(r1, hd), r2),
            derivativeStep(r2, hd),
            tl ++ s2
          )
        } else {
          assert(derivativeStep(Concat(r1, r2), hd) == Union(Concat(derivativeStep(r1, hd), r2), EmptyLang()))
          lemmaRegexAcceptsStringThenUnionWithAnotherAcceptsToo(
            Concat(derivativeStep(r1, hd), r2),
            EmptyLang(),
            tl ++ s2
          )
          assert(matchR(Concat(r1, r2), s1 ++ s2))
        }
      }
      case Nil() =>
        lemmaRegexConcatWithNullableAcceptsSameStr(r2, r1, s2)

    }
  } ensuring (_ => matchR(Concat(r1, r2), s1 ++ s2))

  def lemmaFindSeparationIsDefinedThenConcatMatches[C](r1: Regex[C], r2: Regex[C], s1: List[C], s2: List[C], s: List[C]): Unit = {
    require(validRegex(r1))
    require(validRegex(r2))
    require(s == s1 ++ s2)
    require(findConcatSeparation(r1, r2, Nil(), s, s).isDefined)
    require(findConcatSeparation(r1, r2, Nil(), s, s).get == (s1, s2))

    lemmaTwoRegexMatchThenConcatMatchesConcatString(r1, r2, s1, s2)

  } ensuring (_ => matchR(Concat(r1, r2), s1 ++ s2))

  def lemmaR1MatchesS1AndR2MatchesS2ThenFindSeparationFindsAtLeastThem[C](
      r1: Regex[C],
      r2: Regex[C],
      s1: List[C],
      s2: List[C],
      s: List[C],
      s1Rec: List[C],
      s2Rec: List[C]
  ): Unit = {
    require(validRegex(r1))
    require(validRegex(r2))
    require(s1 ++ s2 == s)
    require(ListUtils.isPrefix(s1Rec, s1))
    require(s1Rec ++ s2Rec == s)
    require(matchR(r1, s1))
    require(matchR(r2, s2))
    decreases(s2Rec.size)

    (s1Rec, s2Rec) match {
      case (_, _) if matchR(r1, s1Rec) && matchR(r2, s2Rec) => ()
      case (_, Nil()) => {
        assert(s1Rec.size == s.size)
        assert(s1Rec.size == s1.size)
        assert(s1Rec == s1)
        assert(s2Rec == s2)
        assert(findConcatSeparation(r1, r2, s1Rec, s2Rec, s) == Some(s1Rec, s2Rec))
      }
      case (_, Cons(hd2, tl2)) => {
        ListUtils.lemmaConcatTwoListThenFirstIsPrefix(s1, s2)
        ListUtils.lemmaConcatTwoListThenFirstIsPrefix(s1Rec, s2Rec)
        if (s1Rec == s1) {
          ListUtils.lemmaConcatTwoListThenFirstIsPrefix(s1, s2)
          ListUtils.lemmaSamePrefixThenSameSuffix(s1, s2, s1Rec, s2Rec, s)
          check(false)
        }
        lemmaMoveElementToOtherListKeepsConcatEq(s1Rec, hd2, tl2, s)
        ListUtils.lemmaConcatTwoListThenFirstIsPrefix(s1Rec ++ List(hd2), tl2)
        if (s1Rec.size == s1.size) {
          ListUtils.lemmaIsPrefixSameLengthThenSameList(s1, s1Rec, s)
          check(false)
        }

        ListUtils.lemmaPrefixFromSameListAndStrictlySmallerThenPrefixFromEachOther(s1, s1Rec ++ List(hd2), s)
        lemmaR1MatchesS1AndR2MatchesS2ThenFindSeparationFindsAtLeastThem(r1, r2, s1, s2, s, s1Rec ++ List(hd2), tl2)
      }
    }

  } ensuring (_ => findConcatSeparation(r1, r2, s1Rec, s2Rec, s).isDefined)

  def lemmaConcatAcceptsStringThenFindSeparationIsDefined[C](r1: Regex[C], r2: Regex[C], s: List[C]): Unit = {
    require(validRegex(r1))
    require(validRegex(r2))
    require(matchR(Concat(r1, r2), s))
    decreases(s)

    val r = Concat(r1, r2)
    s match {
      case Cons(hd, tl) => {
        assert(matchR(derivativeStep(Concat(r1, r2), hd), tl))
        //  if (nullable(rOne)) Union(Concat(derivativeStep(rOne, a), rTwo), derivativeStep(rTwo, a))
        // else Union(Concat(derivativeStep(rOne, a), rTwo), EmptyLang())
        if (nullable(r1)) {
          assert(derivativeStep(r, hd) == Union(Concat(derivativeStep(r1, hd), r2), derivativeStep(r2, hd)))
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(r1, hd), r2), derivativeStep(r2, hd), tl)
          if (matchR(Concat(derivativeStep(r1, hd), r2), tl)) {
            lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(r1, hd), r2, tl)
            assert(findConcatSeparation(derivativeStep(r1, hd), r2, Nil(), tl, tl).isDefined)
            val (s1, s2) = findConcatSeparation(derivativeStep(r1, hd), r2, Nil(), tl, tl).get
            lemmaR1MatchesS1AndR2MatchesS2ThenFindSeparationFindsAtLeastThem(r1, r2, Cons(hd, s1), s2, s, Nil(), s)
          } else {
            assert(matchR(derivativeStep(r2, hd), tl))
          }
        } else {
          assert(derivativeStep(r, hd) == Union(Concat(derivativeStep(r1, hd), r2), EmptyLang()))
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(r1, hd), r2), EmptyLang(), tl)
          lemmaEmptyLangDerivativeIsAFixPoint(EmptyLang(), tl)
          assert(matchR(Concat(derivativeStep(r1, hd), r2), tl))
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(r1, hd), r2, tl)
          val (s1, s2) = findConcatSeparation(derivativeStep(r1, hd), r2, Nil(), tl, tl).get
          lemmaR1MatchesS1AndR2MatchesS2ThenFindSeparationFindsAtLeastThem(r1, r2, Cons(hd, s1), s2, s, Nil(), s)
        }
      }
      case Nil() => {
        assert(nullable(r1) && nullable(r2))
        assert(findConcatSeparation(r1, r2, Nil(), Nil(), Nil()) == Some((Nil[C](), Nil[C]())))
      }
    }

  } ensuring (_ => findConcatSeparation(r1, r2, Nil(), s, s).isDefined)

  // Star lemmas
  def lemmaStarAcceptsEmptyString[C](r: Star[C]): Unit = {
    require(validRegex(r))
  } ensuring (_ => matchR(r, List()))

  def lemmaStarApp[C](r: Regex[C], s1: List[C], s2: List[C]): Unit = {
    require(validRegex(Star(r)))
    require(matchR(r, s1))
    require(matchR(Star(r), s2))

    s1 match {
      case Cons(hd, tl) => {
        assert(derivativeStep(Star(r), hd) == Concat(derivativeStep(r, hd), Star(r)))
        lemmaTwoRegexMatchThenConcatMatchesConcatString(derivativeStep(r, hd), Star(r), tl, s2)
      }
      case Nil() => ()
    }
  } ensuring (_ => matchR(Star(r), s1 ++ s2))

  def lemmaStarAppConcat[C](r: Regex[C], s: List[C]): Unit = {
    require(validRegex(Star(r)))
    require(matchR(Star(r), s))

    s match {
      case Cons(hd, tl) => {
        assert(derivativeStep(Star(r), hd) == Concat(derivativeStep(r, hd), Star(r)))
        val r1 = derivativeStep(r, hd)
        val r2 = Star(r)
        lemmaConcatAcceptsStringThenFindSeparationIsDefined(r1, r2, tl)
        val cut = findConcatSeparation(r1, r2, Nil(), tl, tl)
        lemmaTwoRegexMatchThenConcatMatchesConcatString(r, Star(r), Cons(hd, cut.get._1), cut.get._2)
      }
      case Nil() => ()
    }
  } ensuring (_ => s.isEmpty || matchR(Concat(r, Star(r)), s))

  // usedCharacters lemmas ---------------------------------------------------------------------------------------------------

  def lemmaRegexCannotMatchAStringContainingACharItDoesNotContain[C](r: Regex[C], s: List[C], c: C): Unit = {
    require(validRegex(r))
    require(s.contains(c))
    require(!usedCharacters(r).contains(c))
    decreases(s)

    s match {
      case Cons(hd, tl) if hd == c => lemmaRegexCannotMatchAStringStartingWithACharItDoesNotContain(r, s, c)
      case Cons(hd, tl) if hd != c => {
        lemmaDerivativeStepDoesNotAddCharToUsedCharacters(r, hd, c)
        lemmaRegexCannotMatchAStringContainingACharItDoesNotContain(derivativeStep(r, hd), tl, c)
      }
      case Nil() => check(false)
    }

  } ensuring (_ => !matchR(r, s))

  def lemmaRegexCannotMatchAStringStartingWithACharItDoesNotContain[C](r: Regex[C], s: List[C], c: C): Unit = {
    require(validRegex(r))
    require(s.contains(c))
    require(s.head == c)
    require(!usedCharacters(r).contains(c))

    if (matchR(r, s)) {
      lemmaMatchRIsSameAsWholeDerivativeAndNil(r, s)
      lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(r, c, s.tail)
      check(false)
    }

  } ensuring (_ => !matchR(r, s))

  def lemmaRegexCannotMatchAStringStartingWithACharWhichIsNotInFirstChars[C](r: Regex[C], s: List[C], c: C): Unit = {
    require(validRegex(r))
    require(s.contains(c))
    require(s.head == c)
    require(!firstChars(r).contains(c))

    if (matchR(r, s)) {
      lemmaMatchRIsSameAsWholeDerivativeAndNil(r, s)
      lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(r, c, s.tail)
      check(false)
    }

  } ensuring (_ => !matchR(r, s))

  // not used
  def lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC[C](r: Regex[C], c: C): Unit = {
    require(validRegex(r))
    require(!nullable(r))
    require(nullable(derivativeStep(r, c)))
    decreases(r)

    r match {
      case EmptyExpr()     => check(false)
      case EmptyLang()     => ()
      case ElementMatch(a) => ()
      case Union(rOne, rTwo) => {
        if (nullable(rOne)) {
          check(false)
        }
        if (nullable(rTwo)) {
          check(false)
        }
        if (nullable(derivativeStep(rOne, c))) {
          lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC(rOne, c)
        } else {
          assert(nullable(derivativeStep(rTwo, c)))
          lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC(rTwo, c)
        }
      }
      case Star(rInner) => ()
      case Concat(rOne, rTwo) => {
        if (nullable(rOne)) {
          if (nullable(Concat(derivativeStep(rOne, c), rTwo))) {
            lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC(rOne, c)
          } else {
            assert(nullable(derivativeStep(rTwo, c)))
            lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC(rTwo, c)

          }
        } else {
          lemmaRIsNotNullableDerivativeStepIsThenUsedCharContainsC(rOne, c)
        }
      }
    }

  } ensuring (_ => usedCharacters(r).contains(c))

  // DONE
  def lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead[C](r: Regex[C], c: C, tl: List[C]): Unit = {
    require(validRegex(r))
    require(nullable(derivative(derivativeStep(r, c), tl)))
    decreases(r)

    r match {
      case EmptyExpr() => {
        assert(derivativeStep(r, c) == EmptyLang[C]())
        lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
        check(false)
      }
      case EmptyLang() => {
        assert(derivativeStep(r, c) == EmptyLang[C]())
        lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
        check(false)
      }
      case ElementMatch(a) => {
        if (c == a) {
          assert(derivativeStep(r, c) == EmptyExpr[C]())
          if (tl.isEmpty) {
            assert(usedCharacters(r).contains(c))
            assert(nullable(derivative(derivativeStep(r, c), tl)))
          } else {
            lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(derivativeStep(r, c), tl.head), tl.tail)
            check(false)
          }
        } else {
          assert(derivativeStep(r, c) == EmptyLang[C]())
          lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
          check(false)
        }
      }
      case Union(rOne, rTwo) => {
        if (nullable(derivative(derivativeStep(rOne, c), tl))) {
          lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rOne, c, tl)
        } else if (nullable(derivative(derivativeStep(rTwo, c), tl))) {
          lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rTwo, c, tl)
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(r, Cons(c, tl))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(rOne, Cons(c, tl))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(rTwo, Cons(c, tl))
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(rOne, rTwo, Cons(c, tl))
          check(false)
        }
      }
      case Star(rInner) => {
        assert(derivativeStep(r, c) == Concat(derivativeStep(rInner, c), Star(rInner)))
        if (nullable(derivative(derivativeStep(rInner, c), tl))) {
          lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rInner, c, tl)
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(r, c), tl)
          assert(matchR(derivativeStep(r, c), tl))
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rInner, c), Star(rInner), tl)
          val (s1, s2) = findConcatSeparation(derivativeStep(rInner, c), Star(rInner), Nil(), tl, tl).get
          assert(s1 ++ s2 == tl)
          assert(matchR(Star(rInner), s2))

          assert(matchR(rInner, Cons(c, s1)))
          assert(matchR(derivativeStep(rInner, c), s1))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rInner, c), s1)
          lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rInner, c, s1)
        }
      }
      case Concat(rOne, rTwo) => {
        //  if (nullable(rOne)) Union(Concat(derivativeStep(rOne, a), rTwo), derivativeStep(rTwo, a))
        // else Union(Concat(derivativeStep(rOne, a), rTwo), EmptyLang())

        if (nullable(rOne)) {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(Union(Concat(derivativeStep(rOne, c), rTwo), derivativeStep(rTwo, c)), tl)
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(rOne, c), rTwo), derivativeStep(rTwo, c), tl)
          if (matchR(Concat(derivativeStep(rOne, c), rTwo), tl)) {

            lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rOne, c), rTwo, tl)
            val (s1, s2) = findConcatSeparation(derivativeStep(rOne, c), rTwo, Nil(), tl, tl).get
            assert(s1 ++ s2 == tl)
            assert(matchR(derivativeStep(rOne, c), s1))
            assert(matchR(rTwo, s2))
            assert(matchR(rOne, Cons(c, s1)))
            lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rOne, c), s1)
            lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rOne, c, s1)
          } else {
            lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rTwo, c), tl)
            lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rTwo, c, tl)
          }
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(Union(Concat(derivativeStep(rOne, c), rTwo), EmptyLang()), tl)
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(rOne, c), rTwo), EmptyLang(), tl)
          lemmaEmptyLangDerivativeIsAFixPoint(EmptyLang(), tl)
          assert(matchR(Concat(derivativeStep(rOne, c), rTwo), tl))
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rOne, c), rTwo, tl)
          val (s1, s2) = findConcatSeparation(derivativeStep(rOne, c), rTwo, Nil(), tl, tl).get
          assert(s1 ++ s2 == tl)
          assert(matchR(derivativeStep(rOne, c), s1))
          assert(matchR(rTwo, s2))
          assert(matchR(rOne, Cons(c, s1)))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rOne, c), s1)
          lemmaDerivativeAfterDerivativeStepIsNullableThenUsedCharsContainsHead(rOne, c, s1)

        }
      }
    }

  } ensuring (_ => usedCharacters(r).contains(c))

  def lemmaDerivativeStepDoesNotAddCharToUsedCharacters[C](r: Regex[C], c: C, cNot: C): Unit = {
    decreases(r)
    require(validRegex(r))
    require(!usedCharacters(r).contains(cNot))

    r match {
      case EmptyExpr()     => ()
      case EmptyLang()     => ()
      case ElementMatch(c) => ()
      case Union(rOne, rTwo) => {
        lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rOne, c, cNot)
        lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rTwo, c, cNot)
        lemmaConcatTwoListsWhichNotContainThenTotNotContain(usedCharacters(derivativeStep(rOne, c)), usedCharacters(derivativeStep(rTwo, c)), cNot)
      }
      case Star(rInner) => {
        lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rInner, c, cNot)
      }
      case Concat(rOne, rTwo) => {
        if (nullable(rOne)) {
          lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rOne, c, cNot)
          lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rTwo, c, cNot)
          lemmaConcatTwoListsWhichNotContainThenTotNotContain(usedCharacters(derivativeStep(rOne, c)), usedCharacters(derivativeStep(rTwo, c)), cNot)
        } else {
          lemmaDerivativeStepDoesNotAddCharToUsedCharacters(rOne, c, cNot)
        }
      }
    }

  } ensuring (_ => !usedCharacters(derivativeStep(r, c)).contains(cNot))

  def lemmaEmptyLangDerivativeIsAFixPoint[C](r: Regex[C], s: List[C]): Unit = {
    require(r == EmptyLang[C]())
    s match {
      case Cons(hd, tl) => lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, hd), tl)
      case Nil()        => ()
    }

  } ensuring (_ => derivative(r, s) == r)

  def lemmaUsedCharsContainsAllFirstChars[C](r: Regex[C], c: C): Unit = {
    require(validRegex(r))
    require(firstChars(r).contains(c))
    decreases(r)
    r match {
      case EmptyExpr()     => ()
      case EmptyLang()     => ()
      case ElementMatch(c) => ()
      case Star(r)         => lemmaUsedCharsContainsAllFirstChars(r, c)
      case Union(rOne, rTwo) =>
        if (firstChars(rOne).contains(c)) {
          lemmaUsedCharsContainsAllFirstChars(rOne, c)
        } else {
          lemmaUsedCharsContainsAllFirstChars(rTwo, c)
        }

      case Concat(rOne, rTwo) if nullable(rOne) =>
        if (firstChars(rOne).contains(c)) {
          lemmaUsedCharsContainsAllFirstChars(rOne, c)
        } else {
          lemmaUsedCharsContainsAllFirstChars(rTwo, c)
        }

      case Concat(rOne, rTwo) if !nullable(rOne) => lemmaUsedCharsContainsAllFirstChars(rOne, c)
    }

  } ensuring (_ => usedCharacters(r).contains(c))

  def lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead[C](r: Regex[C], c: C, tl: List[C]): Unit = {
    require(validRegex(r))
    require(nullable(derivative(derivativeStep(r, c), tl)))

    r match {
      case EmptyExpr() => {
        assert(derivativeStep(r, c) == EmptyLang[C]())
        lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
        check(false)
      }
      case EmptyLang() => {
        assert(derivativeStep(r, c) == EmptyLang[C]())
        lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
        check(false)
      }
      case ElementMatch(a) => {
        if (c == a) {
          assert(derivativeStep(r, c) == EmptyExpr[C]())
          if (tl.isEmpty) {
            assert(firstChars(r).contains(c))
            assert(nullable(derivative(derivativeStep(r, c), tl)))
          } else {
            lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(derivativeStep(r, c), tl.head), tl.tail)
            check(false)
          }
        } else {
          assert(derivativeStep(r, c) == EmptyLang[C]())
          lemmaEmptyLangDerivativeIsAFixPoint(derivativeStep(r, c), tl)
          check(false)
        }
      }
      case Union(rOne, rTwo) => {
        if (nullable(derivative(derivativeStep(rOne, c), tl))) {
          lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rOne, c, tl)
        } else if (nullable(derivative(derivativeStep(rTwo, c), tl))) {
          lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rTwo, c, tl)
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(r, Cons(c, tl))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(rOne, Cons(c, tl))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(rTwo, Cons(c, tl))
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(rOne, rTwo, Cons(c, tl))
          check(false)
        }
      }
      case Star(rInner) => {
        assert(derivativeStep(r, c) == Concat(derivativeStep(rInner, c), Star(rInner)))
        if (nullable(derivative(derivativeStep(rInner, c), tl))) {
          lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rInner, c, tl)
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(r, c), tl)
          assert(matchR(derivativeStep(r, c), tl))
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rInner, c), Star(rInner), tl)
          val (s1, s2) = findConcatSeparation(derivativeStep(rInner, c), Star(rInner), Nil(), tl, tl).get
          assert(s1 ++ s2 == tl)
          assert(matchR(Star(rInner), s2))

          assert(matchR(rInner, Cons(c, s1)))
          assert(matchR(derivativeStep(rInner, c), s1))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rInner, c), s1)
          lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rInner, c, s1)
        }
      }
      case Concat(rOne, rTwo) => {
        if (nullable(rOne)) {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(Union(Concat(derivativeStep(rOne, c), rTwo), derivativeStep(rTwo, c)), tl)
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(rOne, c), rTwo), derivativeStep(rTwo, c), tl)
          if (matchR(Concat(derivativeStep(rOne, c), rTwo), tl)) {

            lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rOne, c), rTwo, tl)
            val (s1, s2) = findConcatSeparation(derivativeStep(rOne, c), rTwo, Nil(), tl, tl).get
            assert(s1 ++ s2 == tl)
            assert(matchR(derivativeStep(rOne, c), s1))
            assert(matchR(rTwo, s2))
            assert(matchR(rOne, Cons(c, s1)))
            lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rOne, c), s1)
            lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rOne, c, s1)
          } else {
            lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rTwo, c), tl)
            lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rTwo, c, tl)
          }
        } else {
          lemmaMatchRIsSameAsWholeDerivativeAndNil(Union(Concat(derivativeStep(rOne, c), rTwo), EmptyLang()), tl)
          lemmaRegexUnionAcceptsThenOneOfTheTwoAccepts(Concat(derivativeStep(rOne, c), rTwo), EmptyLang(), tl)
          lemmaEmptyLangDerivativeIsAFixPoint(EmptyLang(), tl)
          assert(matchR(Concat(derivativeStep(rOne, c), rTwo), tl))
          lemmaConcatAcceptsStringThenFindSeparationIsDefined(derivativeStep(rOne, c), rTwo, tl)
          val (s1, s2) = findConcatSeparation(derivativeStep(rOne, c), rTwo, Nil(), tl, tl).get
          assert(s1 ++ s2 == tl)
          assert(matchR(derivativeStep(rOne, c), s1))
          assert(matchR(rTwo, s2))
          assert(matchR(rOne, Cons(c, s1)))
          lemmaMatchRIsSameAsWholeDerivativeAndNil(derivativeStep(rOne, c), s1)
          lemmaDerivAfterDerivStepIsNullableThenFirstCharsContainsHead(rOne, c, s1)

        }
      }
    }

  } ensuring (_ => firstChars(r).contains(c))
}

object Utils {
  def maxBigInt(a: BigInt, b: BigInt): BigInt = if (a >= b) a else b
  def maxLong(a: Long, b: Long): Long = if (a >= b) a else b
}