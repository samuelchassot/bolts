package ch.epfl.lexer

// import VerifiedRegex.*
import stainless.collection.List
import stainless.collection.Nil
import stainless.collection.Cons
import stainless.annotation.law
import stainless.annotation.ghost
import stainless.lang.StaticChecks.*
import stainless.annotation.opaque


abstract class Regex[C]

// This is a tradeoff so that we can have different types in different tokens/rules
trait TokenValue

trait Bijection[C] {
  def toValue(l: List[C]): TokenValue
  def toCharacters(t: TokenValue): List[C]

  @law
  def toValueToCharacters(l: List[C]): Boolean = toCharacters(toValue(l)) == l

  @law @ghost
  def toCharactersToValue(t: TokenValue): Boolean = toValue(toCharacters(t)) == t
}

case class Token[C](value: TokenValue, rule: Rule[C], @ghost originalCharacters: List[C]) {
  require(originalCharacters == rule.transformation.toCharacters(value))
  def characters: List[C] = {
    rule.transformation.toCharacters(value)
  }.ensuring(res => res == originalCharacters)
}
case class Rule[C](regex: Regex[C], tag: String, isSeparator: Boolean, transformation: Bijection[C])

trait LexerInterface {

}

case class IdentifierValue(value: List[Char]) extends TokenValue
case object IdentifierValueBijection extends Bijection[Char]:
    def toValue(l: List[Char]): TokenValue = IdentifierValue(l)
    def toCharacters(t: TokenValue): List[Char] = t match
        case IdentifierValue(value) => value
        case _ => Nil[Char]()
end IdentifierValueBijection
