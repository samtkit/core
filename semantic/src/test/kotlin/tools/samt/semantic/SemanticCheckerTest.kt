package tools.samt.semantic

import org.junit.jupiter.api.Nested
import tools.samt.common.DiagnosticContext
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.checks.ValidAnnotationParameterExpressionCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SemanticCheckerTest {

    @Nested
    inner class ValidAnnotationParameterExpression {
        @Test
        fun `can use literal types as annotation arguments`() {
            val source = """
                package annotated

                @Foo(42)
                record Annotated {
                    @Bar("a", 1.5, false)
                    value: Any
                }

                @Secret("password")
                service AnnotatedService {
                    @Important(true)
                    foo(@Logged arg: Any): Any
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidAnnotationParameterExpressionCheck, emptyList())
        }

        @Test
        fun `cannot use complex types in annotations`() {
            val source = """
                package annotated

                @Foo({ a: "a", b: "b" })
                record Annotated {
                    @Bar(true, false, constant)
                    value: Any
                }

                @Secret([true, false])
                service AnnotatedService {
                    @Important(null)
                    foo(@Logged(*) arg: Any): Any
                }
            """.trimIndent()
            parseAndCheck(source, ::ValidAnnotationParameterExpressionCheck, listOf(
                "Error: Invalid annotation argument",
                "Error: Invalid annotation argument",
                "Error: Invalid annotation argument",
                "Error: Invalid annotation argument",
                "Error: Invalid annotation argument",
            ))
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
