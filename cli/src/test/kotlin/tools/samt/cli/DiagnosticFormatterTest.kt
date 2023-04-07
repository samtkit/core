package tools.samt.cli

import tools.samt.common.DiagnosticContext
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.EnumDeclarationNode
import tools.samt.parser.FileNode
import tools.samt.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticFormatterTest {
    @Test
    fun `global messages`() {
        val controller = DiagnosticController("/tmp")
        controller.reportGlobalError("This is a global error")
        controller.reportGlobalWarning("This is a global warning")
        controller.reportGlobalInfo("This is a global info")
        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: This is a global error
            
            ────────────────────────────────────────
            WARNING: This is a global warning
            
            ────────────────────────────────────────
            INFO: This is a global info
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 1 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `file messages with no highlights`() {
        val controller = DiagnosticController("/tmp")
        val source = ""
        val sourceFile = SourceFile("/tmp/test.txt", source)
        val context = controller.createContext(sourceFile)

        context.error {
            message("some error")
        }

        context.warn {
            message("some warning")
        }

        context.info {
            message("some info")
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> test.txt
            
            ────────────────────────────────────────
            WARNING: some warning
             ---> test.txt
            
            ────────────────────────────────────────
            INFO: some info
             ---> test.txt
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 1 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `multiline highlight with no message`() {
        val source = """
            package debug
            enum Test {
                Foo, Bar, Baz
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight(enumNode.location)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:2:1
            
                  1 │ package debug
            |>    2 │ enum Test {
            |>    3 │     Foo, Bar, Baz
            |>    4 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `multiline highlight with a message`() {
        val source = """
            package debug
            enum Test {
                Foo, Bar, Baz
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight("some highlight", enumNode.location)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:2:1
            
                  1 │ package debug
            |>    2 │ enum Test {
            |>    3 │     Foo, Bar, Baz
            |>    4 │ }
            |       │ 
            +--------- some highlight
                    │ 
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `highlights with overlapping context areas`() {
        val source = """
            package debug
            enum Test {
                Foo,
                Bar,
                Baz,
                Qux
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight(enumNode.values.first().location)
            highlight("some highlight", enumNode.values.last().location)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:3:5
            
                  1 │ package debug
                  2 │ enum Test {
                  3 │     Foo,
                    │     ^^^
                    │ 
                  4 │     Bar,
                  5 │     Baz,
                  6 │     Qux
                    │     ^^^
                    │     |
                    │     some highlight
                    │ 
                  7 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `highlights with non-overlapping context areas`() {
        val source = """
            package debug
            enum Test {
                Foo,
                Bar,
                Bar,
                Bar,
                Bar,
                Bar,
                Bar,
                Bar,
                Bar,
                Bar,
                Qux
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight("some highlight 1", enumNode.values.first().location)
            highlight("some highlight 2", enumNode.values.last().location)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:3:5
            
                  1 │ package debug
                  2 │ enum Test {
                  3 │     Foo,
                    │     ^^^
                    │     |
                    │     some highlight 1
                    │ 
                  4 │     Bar,
                  5 │     Bar,
                  6 │     Bar,
                ... 
                 10 │     Bar,
                 11 │     Bar,
                 12 │     Bar,
                 13 │     Qux
                    │     ^^^
                    │     |
                    │     some highlight 2
                    │ 
                 14 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `multiple single-line highlights`() {
        val source = """
            package debug
            enum Test {
                Foo, Bar, Baz
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            for (value in enumNode.values) {
                highlight("enum value '${value.name}'", value.location)
            }
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:3:5
            
                  1 │ package debug
                  2 │ enum Test {
                  3 │     Foo, Bar, Baz
                    │     ^^^  ^^^  ^^^
                    │     |    |    |
                    │     |    |    enum value 'Baz'
                    │     |    |
                    │     |    enum value 'Bar'
                    │     |
                    │     enum value 'Foo'
                    │ 
                  4 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `message annotations`() {
        val source = """
            package debug
            enum Test {
                Foo
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight("some highlight", enumNode.values.single().location)
            info("info annotation")
            help("help annotation")
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:3:5
            
                  1 │ package debug
                  2 │ enum Test {
                  3 │     Foo
                    │     ^^^
                    │     |
                    │     some highlight
                    │ 
                  4 │ }
            
                    = info: info annotation
                    = help: help annotation
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `highlight beginning only flag with no message`() {
        val source = """
            package debug
            enum Test {
                Foo
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight(enumNode.location, highlightBeginningOnly = true)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:2:1
            
                  1 │ package debug
                  2 │ enum Test {
                    │ ^
                    │ 
                  3 │     Foo
                  4 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `highlight beginning only flag with a message`() {
        val source = """
            package debug
            enum Test {
                Foo
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight("some highlight", enumNode.location, highlightBeginningOnly = true)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:2:1
            
                  1 │ package debug
                  2 │ enum Test {
                    │ ^
                    │ |
                    │ some highlight
                    │ 
                  3 │     Foo
                  4 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    @Test
    fun `highlight beginning only flag mixed with regular highlight`() {
        val source = """
            package debug
            enum Test {
                Foo
            }
        """.trimIndent()

        val (fileNode, context, controller) = parse(source)
        assert(fileNode.statements.single() is EnumDeclarationNode)
        val enumNode = fileNode.statements.single() as EnumDeclarationNode

        context.error {
            message("some error")
            highlight("enum begin", enumNode.location, highlightBeginningOnly = true)
            highlight("enum name", enumNode.name.location)
        }

        val output = DiagnosticFormatter.format(controller, 0, 0, terminalWidth = 40)
        val outputWithoutColors = output.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            ────────────────────────────────────────
            ERROR: some error
             ---> DiagnosticFormatterTest.samt:2:1
            
                  1 │ package debug
                  2 │ enum Test {
                    │ ^    ^^^^
                    │ |    |
                    │ |    enum name
                    │ |
                    │ enum begin
                    │ 
                  3 │     Foo
                  4 │ }
            
            ────────────────────────────────────────
            BUILD FAILED in 0ms (1 error(s), 0 warning(s))
        """.trimIndent().trim(), outputWithoutColors.trimIndent().trim())
    }

    private fun parse(source: String): Triple<FileNode, DiagnosticContext, DiagnosticController> {
        val filePath = "/tmp/DiagnosticFormatterTest.samt"
        val sourceFile = SourceFile(filePath, source)
        val diagnosticController = DiagnosticController("/tmp")
        val diagnosticContext = diagnosticController.createContext(sourceFile)
        val stream = Lexer.scan(source.reader(), diagnosticContext)
        val fileTree = Parser.parse(sourceFile, stream, diagnosticContext)
        assertFalse(diagnosticContext.hasErrors(), "Expected no errors, but had errors")
        return Triple(fileTree, diagnosticContext, diagnosticController)
    }
}
