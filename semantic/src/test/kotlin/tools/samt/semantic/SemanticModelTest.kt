package tools.samt.semantic

import org.junit.jupiter.api.Nested
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SemanticModelTest {

    @Nested
    inner class UniquenessChecks {
        @Test
        fun `cannot have duplicate enum value`() {
            val source = """
                package color

                enum Color {
                    Red,
                    Green,
                    Blue,
                    Red
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Enum value 'Red' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate record field`() {
            val source = """
                package color

                record Color {
                    red: Int
                    green: Int
                    blue: Int
                    red: Int
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Record field 'red' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate service operation`() {
            val source = """
                package color

                record Color

                service ColorService {
                    get(): Color
                    set(color: Color)
                    get()
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Operation 'get' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate declarations with same name`() {
            val source = """
                package color

                record A
                record A

                record B
                enum B { }

                service C { }
                provide C {
                    implements C
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: 'A' is already declared",
                    "Error: 'B' is already declared",
                    "Error: 'C' is already declared"
                )
            )
        }

        @Test
        fun `cannot import same type multiple times`() {
            val foo = """
                import bar.B
                import bar.*

                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A
                import foo.A

                package bar

                record B
            """.trimIndent()
            parseAndCheck(
                foo to listOf(
                    "Error: Import 'B' conflicts with locally defined type with same name"
                ),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `cannot import type which is defined locally`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A

                package bar

                enum A { }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `can import type with same name if using import alias`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A as OtherA

                package bar

                record A {
                    a: OtherA
                }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to emptyList(),
            )
        }

        @Test
        fun `can reference type with same name via package`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo as fooAlias

                package bar

                record A {
                    a1: foo.A
                    a2: fooAlias.A
                }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to emptyList(),
            )
        }

        @Test
        fun `cannot import type through wildcard which is defined locally`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.*

                package bar

                enum A { }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `warn about user overriding built-in type`() {
            val source = """
                package foo

                record String
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Type 'String' shadows built-in type with same name"),
            )
        }
    }

    @Nested
    inner class OptionalTypes {
        @Test
        fun `warn about duplicate nullability`() {
            val source = """
                package color

                record Color {
                    red: Int?
                    green: Int??
                    blue: Int?
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Warning: Type is already optional, ignoring '?'"))
        }

        @Test
        fun `warn about indirect duplicate nullability`() {
            val source = """
                package color

                record Color {
                    red: Int? (range(1..100))?
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Warning: Type is already optional, ignoring '?'"))
        }
    }

    @Nested
    inner class Constraints {
        @Test
        fun `cannot use constraints on bad base type`() {
            val source = """
                package complex

                record Complex {
                    int: Int? (pattern("a-z"))
                    list: List<String> (pattern("a-z"))
                    int2: Int (size(1..100))
                    string: String (value(42))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Constraint 'pattern(a-z)' is not allowed for type 'Int'",
                    "Error: Constraint 'pattern(a-z)' is not allowed for type 'List<String>'",
                    "Error: Constraint 'size(1..100)' is not allowed for type 'Int'",
                    "Error: Constraint 'value(42)' is not allowed for type 'String'",
                )
            )
        }

        @Test
        fun `range must be valid`() {
            val source = """
                package complex

                record Complex {
                    int: Int (range(1 .. "2"))
                }

                service Foo {
                    bar(value: Float (range(* .. *)))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Range constraint argument must be a valid number range",
                    "Error: Range constraint must have at least one valid number",
                )
            )
        }

        @Test
        fun `size must be valid`() {
            val source = """
                package complex

                record Complex {
                    int: List<Int> (size(1..5.5))
                }

                service Foo {
                    bar(): String (size(5..4))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Expected size constraint argument to be a whole number or wildcard",
                    "Error: Size constraint lower bound must be lower than or equal to the upper bound",
                )
            )
        }

        @Test
        fun `cannot use non-existent constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (max(100))
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Constraint with name 'max' does not exist"))
        }

        @Test
        fun `can use shorthand constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (100)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `cannot nest constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (range(1..100)) (range(1..*))
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Cannot have nested constraints"))
        }

        @Test
        fun `cannot illegal arguments to constraints`() {
            val range = """
                package range

                record Range {
                    foo: Int? (range(1.."100"))
                }
            """.trimIndent()
            val size = """
                package size

                record Size {
                    foo: String (size(1, 100))
                }
            """.trimIndent()
            val pattern = """
                package pattern

                record Pattern {
                    foo: String (size(1..100), pattern("a", "-", "z"))
                }
            """.trimIndent()
            parseAndCheck(
                range to listOf("Error: Range constraint argument must be a valid number range"),
                size to listOf("Error: Size constraint must have exactly one size argument"),
                pattern to listOf("Error: Pattern constraint must have exactly one string argument"),
            )
        }
    }

    @Nested
    inner class Generics {
        @Test
        fun `can use nested generics in lists`() {
            val source = """
                package lists

                record Lists {
                    lists: List<List<Int> (size(1..100))> (1..*)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `can use nested generics in map`() {
            val source = """
                package maps

                record Maps {
                    maps: Map<String, Map<String (pattern("a-z")), List<Int>>> (1..*)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `cannot use unknown generics in map`() {
            val source = """
                package foo

                record Foo {
                    foo: Foo<String>
                    badOptionalList: List?<String>
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Unsupported generic type", "Error: Unsupported generic type"))
        }

        @Test
        fun `cannot pass too many or too few generic arguments`() {
            val source = """
                package foo

                record Foo {
                    twoArgList: List<Int, String>
                    oneArgMap: Map<String>
                    threeArgMap: Map<String, String, String>
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Unsupported generic type",
                    "Error: Unsupported generic type",
                    "Error: Unsupported generic type",
                )
            )
        }
    }

    @Nested
    inner class InvalidTypeExpression {
        @Test
        fun `cannot use literal value as type`() {
            val source = """
                package color

                record Color {
                    value: 42
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Cannot use literal value as type"))
        }

        @Test
        fun `cannot use object value as type`() {
            val source = """
                package color

                record Color {
                    value: { red: Int, green: Int, blue: Int }
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Invalid type expression"))
        }

        @Test
        fun `cannot use array value as type`() {
            val source = """
                package color

                record Color {
                    value: [Int]
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Invalid type expression"))
        }
    }

    @Nested
    inner class NotImplementedFeatures {
        @Test
        fun `cannot use extends keyword`() {
            val source = """
                package color

                record Color extends Int
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Record extends are not yet supported")
            )
        }

        @Test
        fun `cannot use type aliases`() {
            val source = """
                package color

                alias Color: Int
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Type aliases are not yet supported")
            )
        }

        @Test
        fun `cannot use async operations`() {
            val source = """
                package color

                service ColorService {
                    async get(): Int
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Async operations are not yet supported")
            )
        }
    }

    @Nested
    inner class PostProcessor {
        @Test
        fun `cannot use service within record fields`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                record Color {
                    ^service: ColorService
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Cannot use service 'ColorService' as type")
            )
        }

        @Test
        fun `cannot use record within consumer`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                record Color {
                    value: String
                }

                provide FooEndpoint {
                    implements ColorService
                    implements Color
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Expected a service but got 'Color'")
            )
        }

        @Test
        fun `cannot provide operations which do not exist in service`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                provide FooEndpoint {
                    implements ColorService { get, post }
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Operation 'post' not found in service 'ColorService'")
            )
        }

        @Test
        fun `cannot use operations which do not exist in service or are not implemented by provider`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                    post()
                }

                service FooService {
                    bar(): String
                    baz(arg: Float)
                }

                provide FooEndpoint {
                    implements ColorService { get }
                    implements FooService
                    transport HTTP
                }

                consume FooEndpoint {
                    uses ColorService { get, post, put }
                    uses FooService { bar, baz, qux }
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Operation 'post' in service 'ColorService' is not implemented by provider 'FooEndpoint'",
                    "Error: Operation 'put' not found in service 'ColorService'",
                    "Error: Operation 'qux' not found in service 'FooService'",
                )
            )
        }

        @Test
        fun `can use and implement operations which do exist in service`() {
            val consumer = """
                import providers.FooEndpoint as Provider
                import services.FooService as Service

                package consumers

                consume Provider {
                    uses Service { foo }
                }
            """.trimIndent()
            val provider = """
                import services.*
                package providers

                provide FooEndpoint {
                    implements FooService
                    transport HTTP
                }
            """.trimIndent()
            val service = """
                package services

                service FooService {
                    foo(): String
                    baz(arg: Float)
                }
            """.trimIndent()
            parseAndCheck(
                consumer to emptyList(),
                provider to emptyList(),
                service to emptyList(),
            )
        }

        @Test
        fun `cannot use or implement same service multiple times`() {
            val consumer = """
                import providers.FooEndpoint as Provider
                import services.FooService as Service

                package consumers

                consume Provider {
                    uses Service { foo }
                    uses services.FooService
                }
            """.trimIndent()
            val provider = """
                import services.*
                package providers

                provide FooEndpoint {
                    implements FooService
                    implements FooService
                    transport HTTP
                }
            """.trimIndent()
            val service = """
                package services

                service FooService {
                    foo()
                }
            """.trimIndent()
            parseAndCheck(
                consumer to listOf("Error: Service 'FooService' already used"),
                provider to listOf("Error: Service 'FooService' already implemented"),
                service to emptyList(),
            )
        }
    }

    private fun parseAndCheck(
        vararg sourceAndExpectedMessages: Pair<String, List<String>>,
    ) {
        val diagnosticController = DiagnosticController("/tmp")
        val fileTrees = sourceAndExpectedMessages.mapIndexed { index, (source) ->
            val filePath = "/tmp/SemanticModelTest-${index}.samt"
            val sourceFile = SourceFile(filePath, source)
            val parseContext = diagnosticController.createContext(sourceFile)
            val stream = Lexer.scan(source.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val parseMessageCount = diagnosticController.contexts.associate { it.source.content to it.messages.size }

        SemanticModelBuilder.build(fileTrees, diagnosticController)

        for ((source, expectedMessages) in sourceAndExpectedMessages) {
            val messages = diagnosticController.contexts
                .first { it.source.content == source }
                .messages
                .drop(parseMessageCount.getValue(source))
                .map { "${it.severity}: ${it.message}" }
            assertEquals(expectedMessages, messages)
        }
    }
}
