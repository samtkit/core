package lexer

import common.DiagnosticConsole
import common.DiagnosticContext
import java.io.IOException
import java.io.StringReader
import kotlin.test.*

class LexerTest {
    private lateinit var diagnostics: DiagnosticConsole

    @BeforeTest
    fun setup() {
        diagnostics = DiagnosticConsole(DiagnosticContext("test", ""))
    }

    @AfterTest
    fun teardown() {
        diagnostics.messages.forEach { println(it) }
    }

    @Test
    fun `comment only file`() {
        val source = "// Line Comment \r\n/* Block Comment */\n// Line Comment"
        val stream = readTokenStream(source)
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `block comment`() {
        val source = "service /* Comment */ A { }"
        val stream = readTokenStream(source)
        assertIs<ServiceToken>(stream.next())
        assertIdentifierToken("A", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `unclosed block comment`() {
        val source = "/**"
        val stream = readTokenStream(source)
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `line comment`() {
        val source = "record // Comment \r\nA { }"
        val stream = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("A", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `integer boundaries`() {
        val source = "2147483647 -2147483648 2147483649 9999999999999999999"
        val stream = readTokenStream(source)
        assertIntegerToken(2147483647, stream.next())
        assertIntegerToken(-2147483648, stream.next())
        assertIntegerToken(2147483649, stream.next())
        assertIntegerToken(0, stream.next()) // Invalid numbers get converted to 0
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `float boundaries`() {
        val source = "0.3 -0.5"
        val stream = readTokenStream(source)
        assertFloatToken(0.3, stream.next())
        assertFloatToken(-0.5, stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `negative float without whole part`() {
        val source = "-.5"
        val stream = readTokenStream(source)
        assertFloatToken(-0.5, stream.next())
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `float without whole part`() {
        val source = ".5"
        val stream = readTokenStream(source)
        assertIs<PeriodToken>(stream.next())
        assertIntegerToken(5, stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `float without fraction part`() {
        val source = "5."
        val stream = readTokenStream(source)
        assertFloatToken(5.0, stream.next())
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `illegal emoji in identifier`() {
        val source = "record foo🙈 { }"
        val stream = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("foo", stream.next())
        assertIs<OpenBraceToken>(stream.next())
        assertIs<CloseBraceToken>(stream.next())
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `simple string literal`() {
        val source = """"Hello SAMT!""""
        val stream = readTokenStream(source)
        assertStringToken("Hello SAMT!", stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `string literal with escape sequences`() {
        val source = """"Hello \"SAMT\"\r\n    Space indented\r\n\tTab indented!""""
        val stream = readTokenStream(source)
        assertStringToken(
            """
Hello "SAMT"
    Space indented
	Tab indented!
        """.trimIndent().replace("\n", "\r\n"), stream.next()
        )
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `string literal with illegal escape sequences`() {
        val source = """"Dubious \escape""""
        val stream = readTokenStream(source)
        assertStringToken("Dubious scape", stream.next())
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `multiline string literal`() {
        val source = """"Hello
SAMT!""""
        val stream = readTokenStream(source)
        assertStringToken(
            """Hello
SAMT!""", stream.next()
        )
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `unclosed string literal`() {
        val source = """"Hello"""
        val stream = readTokenStream(source)
        assertStringToken("Hello", stream.next())
        assertFalse(stream.hasNext())
        assertTrue(diagnostics.hasErrors())
    }

    @Test
    fun `range between two integers`() {
        val source = """Long ( range(1..*) )"""
        val stream = readTokenStream(source)
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
        val stream = readTokenStream(source)
        assertIdentifierToken("Double", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertIdentifierToken("range", stream.next())
        assertIs<OpenParenthesisToken>(stream.next())
        assertFloatToken(0.01, stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertFloatToken(1.00, stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertIs<CloseParenthesisToken>(stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `range between two identifiers`() {
        val source = """foo.. bar ..baz"""
        val stream = readTokenStream(source)
        assertIdentifierToken("foo", stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIdentifierToken("bar", stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIdentifierToken("baz", stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `escaped keyword is read as identifier`() {
        val source = """record ^record"""
        val stream = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("record", stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
    }

    @Test
    fun `escaped identifier is read as identifier but emits warning`() {
        val source = """record ^foo"""
        val stream = readTokenStream(source)
        assertIs<RecordToken>(stream.next())
        assertIdentifierToken("foo", stream.next())
        assertFalse(stream.hasNext())
        assertFalse(diagnostics.hasErrors())
        assertTrue(diagnostics.hasWarnings())
    }

    @Test
    fun `reader without mark support does not fail on number parse`() {
        val source = "1.5 42..128"
        val stream = Lexer.scan(object : StringReader(source) {
            override fun mark(readAheadLimit: Int): Unit = throw IOException()

            override fun markSupported(): Boolean = false
        }, diagnostics).iterator()
        assertFloatToken(1.5, stream.next())
        assertIntegerToken(42, stream.next())
        assertIs<DoublePeriodToken>(stream.next())
        assertIntegerToken(128, stream.next())
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
            fault
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
            <
            >
            ?
"""
        val stream = Lexer.scan(source.reader(), diagnostics).iterator()

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
        assertIs<FaultToken>(stream.next())
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
        assertIs<LessThanSignToken>(stream.next())
        assertIs<GreaterThanSignToken>(stream.next())
        assertIs<QuestionMarkToken>(stream.next())
    }

    private fun readTokenStream(source: String): Iterator<Token> {
        return Lexer.scan(source.reader(), diagnostics).iterator()
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
}
