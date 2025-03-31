package ch.epfl.lexer.example

import ch.epfl.lexer.TokenValue
import ch.epfl.lexer.Bijection
import stainless.collection.List
import stainless.collection.Cons
import stainless.collection.Nil
import stainless.annotation.extern

import stainless.lang.Exception

object Types:
    case class IntegerValue(value: Int, text: List[Char]) extends TokenValue
    case class IdentifierValue(value: List[Char]) extends TokenValue
    // enum KeywordValue extends TokenValue:
    //     case Abstract
    //     case Case
    //     case Class
    //     case Def
    //     case Else
    //     case Extends
    //     case If
    //     case Match
    //     case Object
    //     case Val
    //     case Error
    //     case Underscore
    //     case End
    //     case MetaConversionError
    // end KeywordValue
    // enum PrimitiveTypeValue extends TokenValue:
    //     case Int32
    //     case Unit
    //     case Boolean
    //     case String
    //     case MetaConversionError
    // end PrimitiveTypeValue
    // enum BooleanLiteralValue extends TokenValue:
    //     case True
    //     case False
    //     case MetaConversionError
    // end BooleanLiteralValue
    // enum OperatorValue extends TokenValue:
    //     case Plus
    //     case Minus
    //     case Times
    //     case Div
    //     case Mod
    //     case Not
    //     case Equal
    //     case LessEqual
    //     case And
    //     case Or
    //     case Concat
    //     case MetaConversionError
    // end OperatorValue
    // case class StringLiteralValue(value: List[Char]) extends TokenValue
    // case class DelimiterValue(value: List[Char]) extends TokenValue
    // case class WhitespaceValue(value: List[Char]) extends TokenValue
    // case class CommentValue(value: List[Char]) extends TokenValue

    // case object IntegerValueBijection extends Bijection[Char]:
    //     @extern def charsToInt(l: List[Char]): Int = l.toScala.mkString("").toInt

    //     def toValue(l: List[Char]): TokenValue = IntegerValue(charsToInt(l), l)
    //     def toCharacters(t: TokenValue): List[Char] = t match
    //         case IntegerValue(_, text) => text
    //         case _ => Nil()
    // end IntegerValueBijection

    case object IdentifierValueBijection extends Bijection[Char]:
        def toValue(l: List[Char]): TokenValue = IdentifierValue(l)
        def toCharacters(t: TokenValue): List[Char] = t match
            case IdentifierValue(value) => value
            case _ => Nil()
    end IdentifierValueBijection

    // case object KeywordValueBijection extends Bijection[Char]:
    //     override def toValue(l: List[Char]): TokenValue = l match
    //         case ll if ll == List('a', 'b', 's', 't', 'r', 'a', 'c', 't') => KeywordValue.Abstract
    //         case ll if ll == List('c', 'a', 's', 'e')                                            => KeywordValue.Case
    //         case ll if ll == List('c', 'l', 'a', 's', 's')                                  => KeywordValue.Class
    //         case ll if ll == List('d', 'e', 'f')                                                       => KeywordValue.Def
    //         case ll if ll == List('e', 'l', 's', 'e')                                             => KeywordValue.Else
    //         case ll if ll == List('e', 'x', 't', 'e', 'n', 'd', 's')            => KeywordValue.Extends
    //         case ll if ll == List('i', 'f')                                                                => KeywordValue.If
    //         case ll if ll == List('m', 'a', 't', 'c', 'h')                                  => KeywordValue.Match
    //         case ll if ll == List('o', 'b', 'j', 'e', 'c', 't')                      => KeywordValue.Object
    //         case ll if ll == List('v', 'a', 'l')                                                      => KeywordValue.Val
    //         case ll if ll == List('e','r', 'r', 'o', 'r')                              => KeywordValue.Error
    //         case ll if ll == List('_')                                                                            => KeywordValue.Underscore
    //         case ll if ll == List('e', 'n', 'd')                                                        => KeywordValue.End
    //         case _                                                                                             => KeywordValue.MetaConversionError
    //     override def toCharacters(t: TokenValue): List[Char] = t match
    //         case KeywordValue.Abstract          => Cons('a', Cons('b', Cons('s', Cons('t', Cons('r', Cons('a', Cons('c', Cons('t', Nil()))))))))
    //         case KeywordValue.Case              => Cons('c', Cons('a', Cons('s', Cons('e', Nil()))))
    //         case KeywordValue.Class             => Cons('c', Cons('l', Cons('a', Cons('s', Cons('s', Nil())))))
    //         case KeywordValue.Def               => Cons('d', Cons('e', Cons('f', Nil())))
    //         case KeywordValue.Else              => Cons('e', Cons('l', Cons('s', Cons('e', Nil()))))
    //         case KeywordValue.Extends           => Cons('e', Cons('x', Cons('t', Cons('e', Cons('n', Cons('d', Cons('s', Nil())))))))
    //         case KeywordValue.If                => Cons('i', Cons('f', Nil()))
    //         case KeywordValue.Match             => Cons('m', Cons('a', Cons('t', Cons('c', Cons('h', Nil())))))
    //         case KeywordValue.Object            => Cons('o', Cons('b', Cons('j', Cons('e', Cons('c', Cons('t', Nil()))))))
    //         case KeywordValue.Val               => Cons('v', Cons('a', Cons('l', Nil())))
    //         case KeywordValue.Error             => Cons('e', Cons('r', Cons('r', Cons('o', Cons('r', Nil())))))
    //         case KeywordValue.Underscore        => Cons('_', Nil())
    //         case KeywordValue.End               => Cons('e', Cons('n', Cons('d', Nil())))
    //         case _                              => Nil()
    // end KeywordValueBijection

    // case object PrimitiveTypeValueBijection extends Bijection[Char]:
    //     def toValue(l: List[Char]): TokenValue = l match
    //         case ll if ll == List('I', 'n', 't', '3', '2') => PrimitiveTypeValue.Int32
    //         case ll if ll == List('U', 'n', 'i','t')            => PrimitiveTypeValue.Unit
    //         case ll if ll == List('B', 'o', 'o', 'l', 'e', 'a', 'n') => PrimitiveTypeValue.Boolean
    //         case ll if ll == List('S', 't', 'r', 'i', 'n', 'g') => PrimitiveTypeValue.String
    //         case _ => PrimitiveTypeValue.MetaConversionError
    //     def toCharacters(t: TokenValue): List[Char] = t match
    //         case PrimitiveTypeValue.Int32               => List('I', 'n', 't', '3', '2')
    //         case PrimitiveTypeValue.Unit                => List('U', 'n', 'i','t')
    //         case PrimitiveTypeValue.Boolean             => List('B', 'o', 'o', 'l', 'e', 'a', 'n')
    //         case PrimitiveTypeValue.String              => List('S', 't', 'r', 'i', 'n', 'g')
    //         case _                                      => Nil()
    // end PrimitiveTypeValueBijection

    // case object BooleanLiteralValueBijection extends Bijection[Char]:
    //     def toValue(l: List[Char]): TokenValue = l match
    //         case ll if ll == List('t', 'r', 'u', 'e') => BooleanLiteralValue.True
    //         case ll if ll == List('f', 'a', 'l', 's', 'e') => BooleanLiteralValue.False
    //         case _ => BooleanLiteralValue.MetaConversionError
    //     def toCharacters(t: TokenValue): List[Char] = t match
    //         case BooleanLiteralValue.True  => Cons('t', Cons('r', Cons('u', Cons('e', Nil()))))
    //         case BooleanLiteralValue.False => Cons('f', Cons('a', Cons('l', Cons('s', Cons('e', Nil())))))
    //         case _                         => Nil()
    // end BooleanLiteralValueBijection

    // case object OperatorValueBijection extends Bijection[Char]:
    //     def toValue(l: List[Char]): TokenValue = l match
    //         case ll if ll == List('+') => OperatorValue.Plus
    //         case ll if ll == List('-') => OperatorValue.Minus
    //         case ll if ll == List('*') => OperatorValue.Times
    //         case ll if ll == List('/') => OperatorValue.Div
    //         case ll if ll == List('%') => OperatorValue.Mod
    //         case ll if ll == List('!') => OperatorValue.Not
    //         case ll if ll == List('=') => OperatorValue.Equal
    //         case ll if ll == List('<', '=') => OperatorValue.LessEqual
    //         case ll if ll == List('&', '&') => OperatorValue.And
    //         case ll if ll == List('|', '|') => OperatorValue.Or
    //         case ll if ll == List('+', '+') => OperatorValue.Concat
    //         case _ => OperatorValue.MetaConversionError
    //     def toCharacters(t: TokenValue): List[Char] = t match
    //         case OperatorValue.Plus      => Cons('+', Nil())
    //         case OperatorValue.Minus     => Cons('-', Nil())
    //         case OperatorValue.Times     => Cons('*', Nil())
    //         case OperatorValue.Div       => Cons('/', Nil())
    //         case OperatorValue.Mod       => Cons('%', Nil())
    //         case OperatorValue.Not       => Cons('!', Nil())
    //         case OperatorValue.Equal     => Cons('=', Nil())
    //         case OperatorValue.LessEqual => Cons('<', Cons('=', Nil()))
    //         case OperatorValue.And       => Cons('&', Cons('&', Nil()))
    //         case OperatorValue.Or        => Cons('|', Cons('|', Nil()))
    //         case OperatorValue.Concat    => Cons('+', Cons('+', Nil()))
    //         case _                       => Nil()
    // end OperatorValueBijection

    // case object StringLiteralValueBijection extends Bijection[Char]:
    //     override def toValue(l: List[Char]): TokenValue = StringLiteralValue(l)
    //     override def toCharacters(t: TokenValue): List[Char] =
    //         t match
    //             case StringLiteralValue(value) => value
    //             case _                         => Nil()
    // end StringLiteralValueBijection

    // case object DelimiterValueBijection extends Bijection[Char]:
    //     override def toValue(l: List[Char]): TokenValue = DelimiterValue(l)
    //     override def toCharacters(t: TokenValue): List[Char] = t match
    //         case DelimiterValue(value) => value
    //         case _                     => Nil()
    // end DelimiterValueBijection

    // case object WhitespaceValueBijection extends Bijection[Char]:
    //     override def toValue(l: List[Char]): TokenValue = WhitespaceValue(l)
    //     override def toCharacters(t: TokenValue): List[Char] = 
    //         t match
    //             case WhitespaceValue(value) => value
    //             case _                      => Nil()
    // end WhitespaceValueBijection

    // case object CommentValueBijection extends Bijection[Char]:
    //     override def toValue(l: List[Char]): TokenValue = CommentValue(l)
    //     override def toCharacters(t: TokenValue): List[Char] = 
    //         t match
    //             case CommentValue(value) => value
    //             case _                   => Nil()
    // end CommentValueBijection
end Types
