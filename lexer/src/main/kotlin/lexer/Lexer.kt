package lexer

import common.DiagnosticConsole
import common.FileOffset
import common.Location
import java.io.Reader

class Lexer private constructor(
        private val filePath: String,
        private val reader: Reader,
        private val diagnostics: DiagnosticConsole,
) {
    private var current = '0'
    private var end = false

    /**
     * Index of the last read character, 0 based
     */
    private var currentPosition = FileOffset(charIndex = -1, row = 0, col = -1)

    fun readTokenStream(): Sequence<Token> = sequence {
        // Read very first character
        readNext()
        skipBlanks()
        while (!end) {
            val token = readToken()
            if (token != null) {
                yield(token)
            }
            skipBlanks()
        }
    }

    private fun readToken(): Token? = when {
        current.isDigit() -> readNumber()
        current.isLetter() -> readName()
        else -> when (current) {
            '-' -> readNumber(isNegative = true)
            '"' -> readString()
            '/' -> skipComment()
            '.' -> readDot()
            '^' -> readName(caretPassed = true)
            '{' -> readStaticToken(Tag.OpenBrace)
            '}' -> readStaticToken(Tag.CloseBrace)
            '[' -> readStaticToken(Tag.OpenBracket)
            ']' -> readStaticToken(Tag.CloseBracket)
            '(' -> readStaticToken(Tag.OpenParenthesis)
            ')' -> readStaticToken(Tag.CloseParenthesis)
            ',' -> readStaticToken(Tag.Comma)
            ':' -> readStaticToken(Tag.Colon)
            '*' -> readStaticToken(Tag.Asterisk)
            '@' -> readStaticToken(Tag.AtSign)
            '<' -> readStaticToken(Tag.LessThanSign)
            '>' -> readStaticToken(Tag.GreaterThanSign)
            '?' -> readStaticToken(Tag.QuestionMark)
            else -> {
                val start = currentPosition
                readNext() // Skip unrecognized character
                val errorLocation = locationFromStart(start)
                diagnostics.reportError("Unrecognized character: $current", errorLocation)
                null
            }
        }
    }

    private fun readStaticToken(tag: Tag): StaticToken {
        val start = currentPosition
        readNext()
        return StaticToken(locationFromStart(start), tag)
    }

    private fun readNumber(isNegative: Boolean = false): NumberToken {
        val start = currentPosition

        var hasDecimalPoint = false
        val numberAsString = buildString {
            fun readDigits(): Boolean {
                val previousLength = length
                while (!end && current.isDigit()) {
                    append(current)
                    readNext()
                }
                return length > previousLength
            }

            if (isNegative) {
                assert(current == '-')
                append(current)
                readNext() // Ignore minus for number
            }
            val hasWholeDigits = readDigits()
            if (!hasWholeDigits) {
                diagnostics.reportError("Number is missing whole part (0.5 is valid, .5 is not)", locationFromStart(start))
                append('0')
            }

            if (current == '.') {
                reader.mark(1)
                val positionBeforePeriods = currentPosition
                readNext()

                if (!end && current == '.') {
                    // Range starting with a number, e.g. 1..2
                    // We reset position to the end of the number and return
                    // Because current is already '.', we will read it again in the next iteration
                    currentPosition = positionBeforePeriods
                    reader.reset()
                    return@buildString
                }

                hasDecimalPoint = true

                append('.')
                val hasFractionalDigits = readDigits()

                if (!hasFractionalDigits) {
                    diagnostics.reportError("Number is missing fractional part (0.0 is valid, 0. is not)", locationFromStart(start))
                    append('0')
                }
            }
        }

        val location = locationFromStart(start)
        return if (hasDecimalPoint) {
            val doubleValue = numberAsString.toDoubleOrNull()
            if (doubleValue == null) {
                diagnostics.reportError("Malformed floating point number '$numberAsString'", location)
                FloatToken(location, 0.0)
            } else FloatToken(location, doubleValue)
        } else {
            val longValue = numberAsString.toLongOrNull()
            if (longValue == null) {
                diagnostics.reportError("Malformed whole number '$numberAsString'", location)
                IntegerToken(location, 0)
            } else IntegerToken(location, longValue)
        }
    }

    private fun readName(caretPassed: Boolean = false): Token {
        if (caretPassed) {
            readNext() // Ignore Caret for identifier
        }
        val start = currentPosition

        val name = buildString {
            while (!end && (current.isLetter() || current.isDigit())) {
                append(current)
                readNext()
            }
        }

        val location = locationFromStart(start)

        val parsedKeyword = KEYWORDS[name]

        return when {
            parsedKeyword != null && caretPassed -> IdentifierToken(location, name)
            parsedKeyword != null -> StaticToken(location, parsedKeyword)
            caretPassed -> {
                diagnostics.reportWarning("Escaped word '$name' is not a keyword, please remove unnecessary ^", location)
                IdentifierToken(location, name)
            }

            else -> IdentifierToken(location, name)
        }
    }

    private fun readString(): StringToken {
        assert(current == '"')
        val start = currentPosition
        readNext() // skip leading double quote
        val readString = buildString {
            while (!end && current != '"') {

                if (current == '\\') {
                    readNext()
                    when (current) {
                        't' -> append('\t')
                        'r' -> append('\r')
                        'n' -> append('\n')
                        'b' -> append('\b')
                        '\\' -> append('\\')
                        '"' -> append('"')
                        else -> {
                            diagnostics.reportError(
                                    "Invalid escape sequence: $current", locationFromStart(start)
                            )
                        }
                    }
                    readNext()
                } else {
                    append(current)
                    readNext()
                }
            }
        }
        if (end) {
            diagnostics.reportError(
                    "String was not closed when reaching end of file",
                    locationFromStart(start),
            )
            return StringToken(locationFromStart(start), readString)
        }
        readNext() // skip trailing double quote
        return StringToken(locationFromStart(start), readString)
    }

    private fun readDot(): Token {
        val start = currentPosition
        readNext()
        if (!end) {
            if (current == '.') {
                readNext()
                return StaticToken(locationFromStart(start), Tag.DoublePeriod)
            }
        }

        return StaticToken(locationFromStart(start), Tag.Period)
    }

    private fun skipComment(): Token? {
        readNext()
        if (!end) {
            if (current == '/') {
                skipLineComment()
            } else if (current == '*') {
                skipCommentBlock()
            }
        }
        return null
    }

    private fun skipLineComment(): Token? {
        readNext() // skip second slash
        while (!end && current != '\n') {
            readNext()
        }
        return null
    }

    private fun skipCommentBlock(): Token? {
        val start = currentPosition.copy(charIndex = currentPosition.charIndex - 1, col = currentPosition.col - 1)
        while (!end) {
            readNext()
            if (!end && current == '*') {
                readNext()
                if (!end && current == '/') {
                    readNext()
                    return null
                }
            }
        }
        diagnostics.reportError(
                "Opened block comment was not closed when reaching end of file", locationFromStart(start)
        )
        return null
    }

    private fun locationFromStart(start: FileOffset) = Location(filePath, start, currentPosition)

    private fun skipBlanks() {
        while (!end && (current == ' ' || current == '\n' || current == '\t' || current == '\r')) {
            readNext()
        }
    }

    private fun readNext() {
        val value = reader.read()
        if (value < 0) {
            end = true
        } else {
            current = value.toChar()
        }
        currentPosition = advancedPosition()
    }

    private fun advancedPosition() = if (current == '\n') {
        currentPosition.copy(
                charIndex = currentPosition.charIndex + 1,
                row = currentPosition.row + 1,
                col = 0,
        )
    } else {
        currentPosition.copy(
                charIndex = currentPosition.charIndex + 1,
                col = currentPosition.col + 1,
        )
    }

    companion object {
        private val KEYWORDS = mapOf(
                "record" to Tag.Record,
                "enum" to Tag.Enum,
                "service" to Tag.Service,
                "alias" to Tag.Alias,
                "package" to Tag.Package,
                "import" to Tag.Import,
                "provider" to Tag.Provider,
                "consume" to Tag.Consume,
                "transport" to Tag.Transport,
                "implements" to Tag.Implements,
                "uses" to Tag.Uses,
                "fault" to Tag.Fault,
                "extends" to Tag.Extends,
                "as" to Tag.As,
                "async" to Tag.Async,
                "oneway" to Tag.Oneway,
                "raises" to Tag.Raises,
                "true" to Tag.True,
                "false" to Tag.False,
        )

        fun scan(filePath: String, reader: Reader, diagnostics: DiagnosticConsole): Sequence<Token> {
            return Lexer(filePath, reader, diagnostics).readTokenStream()
        }
    }
}
