package ch.epfl.lexer.example

import ch.epfl.lexer.TokenValue
import ch.epfl.lexer.Bijection
import stainless.collection.List
import stainless.collection.Cons
import stainless.collection.Nil
import stainless.annotation.extern

import stainless.lang.Exception

object Types:
    case class IdentifierValue(value: List[Char]) extends TokenValue
    case object IdentifierValueBijection extends Bijection[Char]:
        def toValue(l: List[Char]): TokenValue = IdentifierValue(l)
        def toCharacters(t: TokenValue): List[Char] = t match
            case IdentifierValue(value) => value
            case _ => Nil()
    end IdentifierValueBijection
end Types
