package tools.samt.lexer

import tools.samt.common.Location
import kotlin.reflect.KClass

sealed interface Token {
    val location: Location
}

data class EndOfFileToken(override val location: Location): Token

sealed interface ValueToken: Token

data class StringToken(override val location: Location, val value: String) : ValueToken
data class IdentifierToken(override val location: Location, val value: String) : ValueToken

sealed interface NumberToken : ValueToken { val value: Number }
data class IntegerToken(override val location: Location, override val value: Long) : NumberToken
data class FloatToken(override val location: Location, override val value: Double) : NumberToken

sealed interface StaticToken: Token
data class RecordToken(override val location: Location): StaticToken
data class EnumToken(override val location: Location): StaticToken
data class ServiceToken(override val location: Location): StaticToken
data class TypealiasToken(override val location: Location): StaticToken
data class PackageToken(override val location: Location): StaticToken
data class ImportToken(override val location: Location): StaticToken
data class ProvideToken(override val location: Location): StaticToken
data class ConsumeToken(override val location: Location): StaticToken
data class TransportToken(override val location: Location): StaticToken
data class ImplementsToken(override val location: Location): StaticToken
data class UsesToken(override val location: Location): StaticToken
data class ExtendsToken(override val location: Location): StaticToken
data class AsToken(override val location: Location): StaticToken
data class AsyncToken(override val location: Location): StaticToken
data class OnewayToken(override val location: Location): StaticToken
data class RaisesToken(override val location: Location): StaticToken
data class TrueToken(override val location: Location): StaticToken
data class FalseToken(override val location: Location): StaticToken

sealed interface StructureToken: Token
data class OpenBraceToken(override val location: Location): StructureToken
data class CloseBraceToken(override val location: Location): StructureToken
data class OpenBracketToken(override val location: Location): StructureToken
data class CloseBracketToken(override val location: Location): StructureToken
data class OpenParenthesisToken(override val location: Location): StructureToken
data class CloseParenthesisToken(override val location: Location): StructureToken
data class CommaToken(override val location: Location): StructureToken
data class PeriodToken(override val location: Location): StructureToken
data class DoublePeriodToken(override val location: Location): StructureToken
data class ColonToken(override val location: Location): StructureToken
data class AsteriskToken(override val location: Location): StructureToken
data class AtSignToken(override val location: Location): StructureToken
data class QuestionMarkToken(override val location: Location): StructureToken
data class EqualsToken(override val location: Location): StructureToken
data class LessThanSignToken(override val location: Location): StructureToken
data class GreaterThanSignToken(override val location: Location): StructureToken
data class ForwardSlashToken(override val location: Location): StructureToken

inline fun <reified T : Token> getHumanReadableName() = getHumanReadableTokenName(T::class)
fun Token.getHumanReadableName() = when(this) {
    is NumberToken -> value.toString()
    is IdentifierToken -> value
    is StringToken -> "\"$value\""
    else -> getHumanReadableTokenName(this::class)
}

fun getHumanReadableTokenName(key: KClass<out Token>): String = when (key) {
    // Token
    EndOfFileToken::class -> "EOF"

    // ValueToken
    StringToken::class -> "string"
    IdentifierToken::class -> "identifier"
    IntegerToken::class -> "integer"
    FloatToken::class -> "float"
    RecordToken::class -> "record"

    // StaticToken
    EnumToken::class -> "enum"
    ServiceToken::class -> "service"
    TypealiasToken::class -> "typealias"
    PackageToken::class -> "package"
    ImportToken::class -> "import"
    ProvideToken::class -> "provide"
    ConsumeToken::class -> "consume"
    TransportToken::class -> "transport"
    ImplementsToken::class -> "implements"
    UsesToken::class -> "uses"
    ExtendsToken::class -> "extends"
    AsToken::class -> "as"
    AsyncToken::class -> "async"
    OnewayToken::class -> "oneway"
    RaisesToken::class -> "raises"
    TrueToken::class -> "true"
    FalseToken::class -> "false"

    // StructureToken
    OpenBraceToken::class -> "{"
    CloseBraceToken::class -> "}"
    OpenBracketToken::class -> "["
    CloseBracketToken::class -> "]"
    OpenParenthesisToken::class -> "("
    CloseParenthesisToken::class -> ")"
    CommaToken::class -> ","
    PeriodToken::class -> "."
    DoublePeriodToken::class -> ".."
    ColonToken::class -> ":"
    AsteriskToken::class -> "*"
    AtSignToken::class -> "@"
    QuestionMarkToken::class -> "?"
    EqualsToken::class -> "="
    LessThanSignToken::class -> "<"
    GreaterThanSignToken::class -> ">"
    ForwardSlashToken::class -> "/"

    // Bug: Missing entry for token type
    else -> throw IllegalArgumentException("Missing entry for token type '${key.simpleName}' in getHumanReadableTokenName lookup table")
}
