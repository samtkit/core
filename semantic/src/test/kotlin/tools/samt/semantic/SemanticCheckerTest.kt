package tools.samt.semantic

import org.junit.jupiter.api.Nested
import tools.samt.common.DiagnosticContext
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.checks.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SemanticCheckerTest {

    @Nested
    inner class NotImplementedFeatures {
        @Test
        fun `cannot use extends keyword`() {
            val source = """
                package color

                record Color extends Byte
            """.trimIndent()
            parseAndCheck(
                source,
                ::NotImplementedFeaturesCheck,
                listOf("Error: Record extends have not yet been implemented")
            )
        }

        @Test
        fun `cannot use type aliases`() {
            val source = """
                package color

                alias Color: Int
            """.trimIndent()
            parseAndCheck(
                source,
                ::NotImplementedFeaturesCheck,
                listOf("Error: Type aliases have not yet been implemented")
            )
        }

        @Test
        fun `cannot use oneway operations`() {
            val source = """
                package color

                service ColorService {
                    oneway get()
                }
            """.trimIndent()
            parseAndCheck(
                source,
                ::NotImplementedFeaturesCheck,
                listOf("Error: Oneway operations have not yet been implemented")
            )
        }

        @Test
        fun `cannot use async operations`() {
            val source = """
                package color

                service ColorService {
                    async get(): Color
                }
            """.trimIndent()
            parseAndCheck(
                source,
                ::NotImplementedFeaturesCheck,
                listOf("Error: Async operations have not yet been implemented")
            )
        }

        @Test
        fun `cannot use operations that raise exceptions`() {
            val source = """
                package color

                service ColorService {
                    get() raises ColorException
                }
            """.trimIndent()
            parseAndCheck(
                source,
                ::NotImplementedFeaturesCheck,
                listOf("Error: Operations that raise exceptions have not yet been implemented")
            )
        }
    }

    @Nested
    inner class UniqueEnumValues {
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
            parseAndCheck(source, ::UniqueEnumValuesCheck, listOf("Error: Enum value 'Red' is defined more than once"))
        }
    }

    @Nested
    inner class UniqueRecordFields {
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
            parseAndCheck(source, ::UniqueRecordFieldsCheck, listOf("Error: Record field 'red' is already defined"))
        }
    }

    @Nested
    inner class UniqueServiceOperations {
        @Test
        fun `cannot have duplicate service operation`() {
            val source = """
                package color

                service ColorService {
                    get(): Color
                    set(color: Color)
                    get()
                }
            """.trimIndent()
            parseAndCheck(source, ::UniqueServiceOperationsCheck, listOf("Error: Operation 'get' is already defined"))
        }
    }

    @Nested
    inner class ValidTypeExpression {
        @Test
        fun `cannot have nested optional type`() {
            val source = """
                package color

                record Color {
                    value: Int?????
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, listOf("Error: Cannot nest optional types"))
        }

        @Test
        fun `cannot use literal value as type`() {
            val source = """
                package color

                record Color {
                    value: 42
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, listOf("Error: Cannot use literal value as type"))
        }

        @Test
        fun `cannot use object value as type`() {
            val source = """
                package color

                record Color {
                    value: { red: Int, green: Int, blue: Int }
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, listOf("Error: Invalid type expression"))
        }

        @Test
        fun `cannot use array value as type`() {
            val source = """
                package color

                record Color {
                    value: [Int]
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, listOf("Error: Invalid type expression"))
        }

        @Test
        fun `can use complex valid types`() {
            val source = """
                package complex

                record Complex {
                    int: Int? (range(1..100), default(50))
                    float: Float (-2.5..2.5)
                    mixed: Number (range(1..10.5), range(1.5..25))
                    string: String (size(1..*))?
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, emptyList())
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
                source,
                ::ValidTypeExpressionCheck,
                listOf(
                    "Error: Range must be between two numbers",
                    "Error: Cannot use two wildcards in a range",
                )
            )
        }

        @Test
        fun `cannot use invalid constraints`() {
            val source = """
                package constraints

                record Constraints {
                    int: Int (Foo(Bar))
                    string: String ("a-z")
                }

                service Foo {
                    Bar(): 42? (answer("for life"))
                }
            """.trimIndent()
            parseAndCheck(
                source,
                ::ValidTypeExpressionCheck,
                listOf(
                    "Error: Invalid type expression",
                    "Error: Invalid constraint construct",
                    "Error: Base type for constraint is not valid",
                )
            )
        }

        @Test
        fun `cannot use generic types`() {
            val source = """
                package color

                record Color {
                    value: List<Int?>? (size(3))
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidTypeExpressionCheck, listOf("Error: Generic type are currently not supported"))
        }
    }

    @Test
    fun `semantic checker passes for valid file`() {
        val source = """
            import tools.samt.Foo

            package tools.samt.semantic
            
            record GreetRequest

            record GreetResponse {
              @Important
              message: String (pattern("a-z"))
              timestamp: DateTime
            }

            service GreetService {
              greet(request: GreetRequest): GreetResponse
            }

            provide GreetProvider {
              implements GreetService
              transport HTTP
            }

            consume GreetProvider {
              uses GreetService { greet }
            }
        """.trimIndent()

        val filePath = "/tmp/SemanticCheckerTest.samt"
        val sourceFile = SourceFile(filePath, source)
        val diagnosticController = DiagnosticController("/tmp")
        val parseContext = diagnosticController.createContext(sourceFile)
        val stream = Lexer.scan(source.reader(), parseContext)
        val fileTree = Parser.parse(sourceFile, stream, parseContext)
        assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors")

        val checkContext = diagnosticController.createContext(sourceFile)
        SemanticChecker.check(fileTree, checkContext)
        assertFalse(checkContext.hasErrors(), "Expected no checker errors, but had errors")
    }

    private fun parseAndCheck(
        source: String,
        checkerFactory: (console: DiagnosticContext) -> SemanticCheck,
        expectedMessages: List<String>,
    ) {
        val filePath = "/tmp/SemanticCheckerTest.samt"
        val sourceFile = SourceFile(filePath, source)
        val diagnosticController = DiagnosticController("/tmp")
        val parseContext = diagnosticController.createContext(sourceFile)
        val stream = Lexer.scan(source.reader(), parseContext)
        val fileTree = Parser.parse(sourceFile, stream, parseContext)
        assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors")

        val checkDiagnostics = diagnosticController.createContext(sourceFile)
        val checker = checkerFactory(checkDiagnostics)
        checker.check(fileTree)

        val messages = checkDiagnostics.messages.map { "${it.severity}: ${it.message}" }
        assertEquals(expectedMessages, messages)
    }
}
