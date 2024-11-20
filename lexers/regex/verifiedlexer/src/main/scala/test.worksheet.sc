import scala.collection.immutable.LazyList.cons
object Utils {
  def maxBigInt(a: BigInt, b: BigInt): BigInt = if (a >= b) a else b
  def maxLong(a: Long, b: Long): Long = if (a >= b) a else b
}

abstract sealed class Regex[C] {}
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

val INT_MAX_VALUE: BigInt = 2147483647
val INT_MAX_VALUE_L: Long = 2147483647L

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

   def isEmptyExpr[C](r: Regex[C]): Boolean = {
    r match {
      case EmptyExpr() => true
      case _           => false
    }
  }
  def isEmptyLang[C](r: Regex[C]): Boolean = {
    r match {
      case EmptyLang() => true
      case _           => false
    }
  }
  def isElementMatch[C](r: Regex[C]): Boolean = {
    r match {
      case ElementMatch(_) => true
      case _               => false
    }
  }
  def elementMatchIsChar[C](r: Regex[C], c: C): Boolean = {
    require(isElementMatch(r))
    r match {
      case ElementMatch(cc) => c == cc
    }
  }
  def isStar[C](r: Regex[C]): Boolean = {
    r match {
      case Star(_) => true
      case _       => false
    }
  }
  def isUnion[C](r: Regex[C]): Boolean = {
    r match {
      case Union(_, _) => true
      case _           => false
    }
  }
  def unionInnersEquals[C](r: Regex[C], r1: Regex[C], r2: Regex[C]): Boolean = {
    require(isUnion(r))
    r match {
      case Union(rOne, rTwo) => r1 == rOne && r2 == rTwo
    }
  }

  def isConcat[C](r: Regex[C]): Boolean = {
    r match {
      case Concat(_, _) => true
      case _            => false
    }
  }

def validRegex[C](r: Regex[C]): Boolean = r match {
  case ElementMatch(c)    => true
  case Star(r)            => !nullable(r) && !isEmptyLang(r) && validRegex(r)
  case Union(rOne, rTwo)  => validRegex(rOne) && validRegex(rTwo)
  case Concat(rOne, rTwo) => validRegex(rOne) && validRegex(rTwo)
  case EmptyExpr()        => true
  case EmptyLang()        => true
}

def regexDepth[C](r: Regex[C]): BigInt = {
  // decreases(r)
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
