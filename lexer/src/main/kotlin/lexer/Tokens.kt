package lexer

import common.Location

sealed interface Token {
    val location: Location
}

enum class Tag {
    // keywords
    Record,
    Enum,
    Service,
    Alias,
    Package,
    Import,
    Provider,
    Consume,
    Transport,
    Implements,
    Uses,
    Fault,
    Extends,
    As,
    Async,
    Oneway,
    Raises,
    True,
    False,

    // punctuation
    OpenBrace,
    CloseBrace,
    OpenBracket,
    CloseBracket,
    OpenParenthesis,
    CloseParenthesis,
    Comma,
    Period,
    DoublePeriod,
    Colon,
    Asterisk,
    AtSign,
    LessThanSign,
    GreaterThanSign,
    QuestionMark,
}

data class StringToken(override val location: Location, val value: String) : Token

data class StaticToken(override val location: Location, val tag: Tag) : Token

sealed interface NumberToken : Token {
    val value: Number
}

data class IntegerToken(override val location: Location, override val value: Long) : NumberToken

data class FloatToken(override val location: Location, override val value: Double) : NumberToken

data class IdentifierToken(override val location: Location, val value: String) : Token
