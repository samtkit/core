package lexer

import common.Location

sealed interface Token {
    val location: Location
}

sealed interface StaticToken: Token
data class RecordToken(override val location: Location): StaticToken
data class EnumToken(override val location: Location): StaticToken
data class ServiceToken(override val location: Location): StaticToken
data class AliasToken(override val location: Location): StaticToken
data class PackageToken(override val location: Location): StaticToken
data class ImportToken(override val location: Location): StaticToken
data class ProvideToken(override val location: Location): StaticToken
data class ConsumeToken(override val location: Location): StaticToken
data class TransportToken(override val location: Location): StaticToken
data class ImplementsToken(override val location: Location): StaticToken
data class UsesToken(override val location: Location): StaticToken
data class FaultToken(override val location: Location): StaticToken
data class ExtendsToken(override val location: Location): StaticToken
data class AsToken(override val location: Location): StaticToken
data class AsyncToken(override val location: Location): StaticToken
data class OnewayToken(override val location: Location): StaticToken
data class RaisesToken(override val location: Location): StaticToken
data class TrueToken(override val location: Location): StaticToken
data class FalseToken(override val location: Location): StaticToken
data class OpenBraceToken(override val location: Location): StaticToken
data class CloseBraceToken(override val location: Location): StaticToken
data class OpenBracketToken(override val location: Location): StaticToken
data class CloseBracketToken(override val location: Location): StaticToken
data class OpenParenthesisToken(override val location: Location): StaticToken
data class CloseParenthesisToken(override val location: Location): StaticToken
data class CommaToken(override val location: Location): StaticToken
data class PeriodToken(override val location: Location): StaticToken
data class DoublePeriodToken(override val location: Location): StaticToken
data class ColonToken(override val location: Location): StaticToken
data class AsteriskToken(override val location: Location): StaticToken
data class AtSignToken(override val location: Location): StaticToken
data class LessThanSignToken(override val location: Location): StaticToken
data class GreaterThanSignToken(override val location: Location): StaticToken
data class QuestionMarkToken(override val location: Location): StaticToken

data class StringToken(override val location: Location, val value: String) : Token

data class IdentifierToken(override val location: Location, val value: String) : Token

sealed interface NumberToken : Token { val value: Number }
data class IntegerToken(override val location: Location, override val value: Long) : NumberToken
data class FloatToken(override val location: Location, override val value: Double) : NumberToken
