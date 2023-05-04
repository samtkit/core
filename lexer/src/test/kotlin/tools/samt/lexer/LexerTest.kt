package tools.samt.lexer

import org.junit.jupiter.api.assertThrows
import tools.samt.common.*
import kotlin.test.*

class LexerTest {
    private var diagnosticController = DiagnosticController("/tmp")

    @Test
    fun `comment only file`() {
        val source = "// Line Comment \r\n/* Block Comment */\n// Line Comment"
        val (stream, context) = readTokenStream(source)
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `block comment`() {
        val source = "service /* Comment */ A { }"
        val (stream, context) = readTokenStream(source)
        assertIs<ServiceToken>(stream.next())
        assertIdentifierToken("A", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `nested block comments`() {
        val source = "service /* outermost comment /* middle comment /* innermost comment */ */ */ A { }"
        val (stream, context) = readTokenStream(source)
        assertIs<ServiceToken>(stream.next())
        assertIdentifierToken("A", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `unclosed block comment`() {
        val source = "/**"
        val (stream, context) = readTokenStream(source)
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `unclosed nested block comments`() {
        val source = "/* /* */"
        val (stream, context) = readTokenStream(source)
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `line comment`() {
        val source = "record // Comment \r\nA { }"
        val (stream, context) = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("A", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `integer boundaries`() {
        val source = "2147483647 -2147483648 2147483649 9999999999999999999"
        val (stream, context) = readTokenStream(source)
        assertIntegerToken(2147483647, stream.next())
        assertIntegerToken(-2147483648, stream.next())
        assertIntegerToken(2147483649, stream.next())
        assertIntegerToken(0, stream.next()) // Invalid numbers get converted to 0
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `float boundaries`() {
        val source = "0.3 -0.5"
        val (stream, context) = readTokenStream(source)
        assertFloatToken(0.3, stream.next())
        assertFloatToken(-0.5, stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `negative float without whole part`() {
        val source = "-.5"
        val (stream, context) = readTokenStream(source)
        assertFloatToken(-0.5, stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `float without whole part`() {
        val source = ".5"
        val (stream, context) = readTokenStream(source)
        assertIs<PeriodToken>(stream.next())
        assertIntegerToken(5, stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `float without fraction part`() {
        val source = "5."
        val (stream, context) = readTokenStream(source)
        assertFloatToken(5.0, stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `illegal emoji in identifier`() {
        val source = "record foo% { }"
        val (stream, context) = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("foo", stream.next())
        val exception = assertThrows<DiagnosticException> {
            assertIs<OpenBraceToken>(stream.next())
        }
        assertEquals("Unrecognized character: '%'", exception.message)
        assertTrue(context.hasErrors())
    }

    @Test
    fun `simple string literal`() {
        val source = """"Hello SAMT!""""
        val (stream, context) = readTokenStream(source)
        assertStringToken("Hello SAMT!", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `string literal with escape sequences`() {
        val source = """"Hello \"SAMT\"\r\n    Space indented\r\n\tTab indented!""""
        val (stream, context) = readTokenStream(source)
        assertStringToken(
            """
Hello "SAMT"
    Space indented
	Tab indented!
        """.trimIndent().replace("\n", "\r\n"), stream.next()
        )
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `string literal with illegal escape sequences`() {
        val source = """"Dubious \escape""""
        val (stream, context) = readTokenStream(source)
        assertStringToken("Dubious scape", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `multiline string literal`() {
        val source = """"Hello
SAMT!""""
        val (stream, context) = readTokenStream(source)
        assertStringToken(
            """Hello
SAMT!""", stream.next()
        )
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `unclosed string literal`() {
        val source = """"Hello"""
        val (stream, context) = readTokenStream(source)
        assertStringToken("Hello", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
    }

    @Test
    fun `range between two integers`() {
        val source = """Long ( range(1..*) )"""
        val (stream, _) = readTokenStream(source)
        assertIdentifierToken("Long", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertIdentifierToken("range", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertIntegerToken(1, stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIs<AsteriskToken>(stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
    }

    @Test
    fun `range between two floats`() {
        val source = """Double ( range(0.01..1.00) )"""
        val (stream, context) = readTokenStream(source)
        assertIdentifierToken("Double", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertIdentifierToken("range", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertFloatToken(0.01, stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertFloatToken(1.00, stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `range between two identifiers`() {
        val source = """foo.. bar ..baz"""
        val (stream, context) = readTokenStream(source)
        assertIdentifierToken("foo", stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIdentifierToken("bar", stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIdentifierToken("baz", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `escaped keyword is read as identifier`() {
        val source = """record ^record"""
        val (stream, context) = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("record", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `escaped identifier is read as identifier but emits warning`() {
        val source = """record ^foo"""
        val (stream, context) = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("foo", stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
        assertTrue(context.hasWarnings())
        assertEquals(DiagnosticSeverity.Warning, context.messages.single().severity)
        assertEquals("Identifier unnecessarily escaped", context.messages.single().message)
    }

    @Test
    fun `missing identifier after escape caret`() {
        val source = """record ^"""
        val (stream, context) = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIs<IdentifierToken>(stream.next())
        assertIs<EndOfFileToken>(stream.next())
        assertTrue(context.hasErrors())
        assertEquals(DiagnosticSeverity.Error, context.messages.single().severity)
        assertEquals("Expected an identifier after caret", context.messages.single().message)
    }

    @Test
    fun `correct location with multiline source`() {
        val source = """
            record
             A
              {
               "Hello"
                }
        """.trimIndent()
        val (stream, context) = readTokenStream(source)
        val record = assertIs<RecordToken>(stream.next())
        assertLocation(FileOffset(0, 0, 0), FileOffset(6, 0, 6), record)
        val identifierA = assertIs<IdentifierToken>(stream.next())
        assertLocation(FileOffset(8, 1, 1), FileOffset(9, 1, 2), identifierA)
        val openBrace = assertIs<OpenBraceToken>(stream.next())
        assertLocation(FileOffset(12, 2, 2), FileOffset(13, 2, 3), openBrace)
        val helloString = assertIs<StringToken>(stream.next())
        assertLocation(FileOffset(17, 3, 3), FileOffset(24, 3, 10), helloString)
        val closeBrace = assertIs<CloseBraceToken>(stream.next())
        assertLocation(FileOffset(29, 4, 4), FileOffset(30, 4, 5), closeBrace)
        assertIs<EndOfFileToken>(stream.next())
        assertFalse(context.hasErrors())
    }

    @Test
    fun `verify all keywords`() {
        val source = """
            record
            enum
            service
            alias
            package
            import
            provide
            consume
            transport
            implements
            uses
            extends
            as
            async
            oneway
            raises
            true
            false
            {
            }
            [
            ]
            (
            )
            ,
            .
            ..
            :
            *
            @
            ?
            =
            <
            >
            /
"""
        val (stream, _) = readTokenStream(source)

        assertIs<RecordToken>(stream.next())
        assertIs<EnumToken>(stream.next())
        assertIs<ServiceToken>(stream.next())
        assertIs<AliasToken>(stream.next())
        assertIs<PackageToken>(stream.next())
        assertIs<ImportToken>(stream.next())
        assertIs<ProvideToken>(stream.next())
        assertIs<ConsumeToken>(stream.next())
        assertIs<TransportToken>(stream.next())
        assertIs<ImplementsToken>(stream.next())
        assertIs<UsesToken>(stream.next())
        assertIs<ExtendsToken>(stream.next())
        assertIs<AsToken>(stream.next())
        assertIs<AsyncToken>(stream.next())
        assertIs<OnewayToken>(stream.next())
        assertIs<RaisesToken>(stream.next())
        assertIs<TrueToken>(stream.next())
        assertIs<FalseToken>(stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertIs<OpenBracketToken>(stream.next())
        assertIs<CloseBracketToken>(stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertIs<CommaToken>(stream.next())
        assertIs<PeriodToken>(stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIs<ColonToken>(stream.next())
        assertIs<AsteriskToken>(stream.next())
        assertIs<AtSignToken>(stream.next())
        assertIs<QuestionMarkToken>(stream.next())
        assertIs<EqualsToken>(stream.next())
        assertIs<LessThanSignToken>(stream.next())
        assertIs<GreaterThanSignToken>(stream.next())
        assertIs<ForwardSlashToken>(stream.next())
    }

    private fun readTokenStream(source: String): Pair<Iterator<Token>, DiagnosticContext> {
        val sourceFile = SourceFile("/tmp/test", source)
        val context = diagnosticController.getOrCreateContext(sourceFile)
        return Pair(Lexer.scan(source.reader(), context).iterator(), context)
    }

    private fun assertIdentifierToken(value: String, actual: Token) {
        assertIs<IdentifierToken>(actual)
        assertEquals(value, actual.value)
    }

    private fun assertIntegerToken(value: Long, actual: Token) {
        assertIs<IntegerToken>(actual)
        assertEquals(value, actual.value)
    }

    private fun assertFloatToken(value: Double, actual: Token) {
        assertIs<FloatToken>(actual)
        assertEquals(value, actual.value)
    }

    private fun assertStringToken(value: String, actual: Token) {
        assertIs<StringToken>(actual)
        assertEquals(value, actual.value)
    }

    private fun assertLocation(start: FileOffset, end: FileOffset, actual: Token) {
        actual.location.let {
            assertEquals(start, it.start, "Expected start to be (${start.charIndex} - ${start.row}:${start.col}) but was (${it.start.charIndex} - ${it.start.row}:${it.start.col})")
            assertEquals(end, it.end, "Expected end to be (${end.charIndex} - ${end.row}:${end.col}) but was (${it.end.charIndex} - ${it.end.row}:${it.end.col})")
        }
    }
}
