package tools.samt.lexer

import tools.samt.common.*
import java.io.BufferedReader
import java.io.Reader

class Lexer private constructor(
    reader: Reader,
    private val diagnostic: DiagnosticContext,
) {
    private var current = '0'
    private var end = false
    private val reader: Reader

    init {
        this.reader = if (reader.markSupported()) reader else BufferedReader(reader)
    }

    /**
     * the starting position of the current token window
     * gets reset after each token is read
     * */
    private var windowStartPosition = FileOffset(charIndex = -1, row = 0, col = -1)

    /**
     * the current position of the lexer
     * */
    private var currentPosition = FileOffset(charIndex = -1, row = 0, col = -1)

    fun readTokenStream(): Sequence<Token> = sequence {
        readNext() // read initial character
        skipBlanks()
        resetStartPosition()
        while (!end) {
            val token = readToken()
            if (token != null) {
                yield(token)
            }
            skipBlanks()
            resetStartPosition()
        }
        while (true) {
           yield(EndOfFileToken(windowLocation()))
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
            '{' -> readStructureToken { OpenBraceToken(it) }
            '}' -> readStructureToken { CloseBraceToken(it) }
            '[' -> readStructureToken { OpenBracketToken(it) }
            ']' -> readStructureToken { CloseBracketToken(it) }
            '(' -> readStructureToken { OpenParenthesisToken(it) }
            ')' -> readStructureToken { CloseParenthesisToken(it) }
            ',' -> readStructureToken { CommaToken(it) }
            ':' -> readStructureToken { ColonToken(it) }
            '*' -> readStructureToken { AsteriskToken(it) }
            '@' -> readStructureToken { AtSignToken(it) }
            '=' -> readStructureToken { EqualsToken(it) }
            '<' -> readStructureToken { LessThanSignToken(it) }
            '>' -> readStructureToken { GreaterThanSignToken(it) }
            '?' -> readStructureToken { QuestionMarkToken(it) }
            else -> {
                val unrecognizedCharacter = current
                readNext() // Skip unrecognized character
                val errorLocation = windowLocation()
                val codeAsHex = unrecognizedCharacter.code.toString(16)

                diagnostic.error {
                    message("Unrecognized character: '$unrecognizedCharacter'")
                    highlight("hex: 0x$codeAsHex", errorLocation)
                    info("The source file must be valid UTF-8")
                }

                null
            }
        }
    }

    private inline fun <reified T: StructureToken> readStructureToken(factory: (location: Location) -> T): StructureToken {
        readNext()
        return factory(windowLocation())
    }

    private fun readNumber(isNegative: Boolean = false): NumberToken {
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
                diagnostic.error {
                    message("Invalid number formatting")
                    highlight("missing whole part", windowLocation())
                    info("0.5 is valid, .5 is not")
                }

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
                    diagnostic.error {
                        message("Invalid number formatting")
                        highlight("missing fractional part", windowLocation())
                        info("5.0 is valid, 5. is not")
                    }

                    append('0')
                }
            }
        }

        val location = windowLocation()
        return if (hasDecimalPoint) {
            val doubleValue = numberAsString.toDoubleOrNull()
            if (doubleValue == null) {
                diagnostic.error {
                    message("Could not parse floating point number")
                    highlight(location)
                }
                FloatToken(location, 0.0)
            } else FloatToken(location, doubleValue)
        } else {
            val longValue = numberAsString.toLongOrNull()
            if (longValue == null) {
                diagnostic.error {
                    message("Could not parse whole number")
                    highlight(location)
                    help("Whole numbers must fit in a 64-bit signed integer")
                    info("The maximum value is ${Long.MAX_VALUE}")
                    info("The minimum value is ${Long.MIN_VALUE}")
                }

                IntegerToken(location, 0)
            } else IntegerToken(location, longValue)
        }
    }

    private fun readName(caretPassed: Boolean = false): Token {
        if (caretPassed) {
            readNext() // Ignore Caret for identifier

            val caretLocation = windowLocation()
            if (!current.isLetter()) {
                diagnostic.error {
                    message("Expected an identifier after caret")
                    highlight(caretLocation)
                    info("Identifiers must start with a letter")
                }

                return IdentifierToken(caretLocation, "^")
            }
        }

        val name = buildString {
            while (!end && (current.isLetter() || current.isDigit())) {
                append(current)
                readNext()
            }
        }

        val location = windowLocation()
        val parsedKeyword = KEYWORDS[name]

        return when {
            parsedKeyword != null && caretPassed -> IdentifierToken(location, name)
            parsedKeyword != null -> parsedKeyword(location)
            caretPassed -> {
                diagnostic.warn {
                    message("Identifier unnecessarily escaped")
                    highlight(location)
                    help("Identifiers must only be escaped if they are valid keywords")
                    info("The following words are keywords: ${KEYWORDS.keys.joinToString(", ")}")
                }

                IdentifierToken(location, name)
            }

            else -> IdentifierToken(location, name)
        }
    }

    private fun readString(): StringToken {
        assert(current == '"')
        readNext() // skip leading double quote
        val readString = buildString {
            while (!end && current != '"') {
                val escapeStartLocation = currentPosition
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
                            val invalidEscapeCharacter = current
                            readNext()
                            val escapeEndLocation = currentPosition
                            val escapeLocation = Location(diagnostic, escapeStartLocation, escapeEndLocation)
                            diagnostic.error {
                                message("Invalid escape sequence: '\\$invalidEscapeCharacter'")
                                highlight(escapeLocation, suggestChange = invalidEscapeCharacter.toString())
                                info("Valid escape sequences are: \\t, \\r, \\n, \\b, \\\\, \\\"")
                            }
                            continue
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
            diagnostic.error {
                message("Unclosed string literal")
                highlight(windowLocation(), highlightBeginningOnly = true)
                help("String literals must be closed with a double quote (\")")
            }

            return StringToken(windowLocation(), readString)
        }
        readNext() // skip trailing double quote
        return StringToken(windowLocation(), readString)
    }

    private fun readDot(): Token {
        readNext()
        if (!end) {
            if (current == '.') {
                readNext()
                return DoublePeriodToken(windowLocation())
            }
        }

        return PeriodToken(windowLocation())
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

    private fun skipLineComment() {
        readNext() // skip second slash
        while (!end && current != '\n') {
            readNext()
        }
    }

    private fun skipCommentBlock() {
        while (!end) {
            readNext()
            if (!end && current == '*') {
                readNext()
                if (!end && current == '/') {
                    readNext()
                    return
                }
            }
        }
        diagnostic.error {
            message("Unclosed block comment")
            highlight(windowLocation(), highlightBeginningOnly = true)
            help("Block comments must be closed with a */")
        }

        return
    }

    private fun windowLocation() = Location(diagnostic, windowStartPosition, currentPosition)

    private fun skipBlanks() {
        while (!end && (current == ' ' || current == '\n' || current == '\t' || current == '\r')) {
            readNext()
        }
    }

    private fun resetStartPosition() {
        windowStartPosition = currentPosition.copy()
    }

    private fun readNext() {
        val previousWasNewline = current == '\n'

        val value = reader.read()
        if (value < 0) {
            end = true
        } else {
            current = value.toChar()
        }

        if (previousWasNewline) {
            incrementRow()
        } else {
            incrementColumn()
        }
    }

    private fun incrementColumn() {
        currentPosition = currentPosition.copy(
            charIndex = currentPosition.charIndex + 1,
            col = currentPosition.col + 1
        )
    }

    private fun incrementRow() {
        currentPosition = currentPosition.copy(
            charIndex = currentPosition.charIndex + 1,
            row = currentPosition.row + 1,
            col = 0
        )
    }

    companion object {
        val KEYWORDS: Map<String, (location: Location) -> StaticToken> = mapOf(
                "record" to { RecordToken(it) },
                "enum" to { EnumToken(it) },
                "service" to { ServiceToken(it) },
                "alias" to { AliasToken(it) },
                "package" to { PackageToken(it) },
                "import" to { ImportToken(it) },
                "provide" to { ProvideToken(it) },
                "consume" to { ConsumeToken(it) },
                "transport" to { TransportToken(it) },
                "implements" to { ImplementsToken(it) },
                "uses" to { UsesToken(it) },
                "extends" to { ExtendsToken(it) },
                "as" to { AsToken(it) },
                "async" to { AsyncToken(it) },
                "oneway" to { OnewayToken(it) },
                "raises" to { RaisesToken(it) },
                "true" to { TrueToken(it) },
                "false" to { FalseToken(it) },
        )

        fun scan(reader: Reader, diagnostics: DiagnosticContext): Sequence<Token> {
            return Lexer(reader, diagnostics).readTokenStream()
        }
    }
}
