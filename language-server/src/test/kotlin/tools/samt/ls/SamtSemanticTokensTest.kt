package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModelBuilder
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import tools.samt.ls.SamtSemanticTokens.Metadata as Meta
import tools.samt.ls.SamtSemanticTokens.TokenModifier as Mod
import tools.samt.ls.SamtSemanticTokens.TokenType as T

class SamtSemanticTokensTest {
    @Test
    fun `correctly tokenizes complex model`() {
        val serviceSource = """
            package test

            enum Age { Underage, Legal, Senior }

            record Person {
                name: List<String? (size(1..100))>
                age: Age
            }

            service PersonService {
                async getNeighbors(person: Person): Map<String, Person> (*..100)
                oneway reloadNeighbors()
            }
        """.trimIndent()
        val providerSource = """
            package test

            provide PersonEndpoint {
                implements PersonService { getNeighbors, reloadNeighbors }

                transport HTTP
            }
        """.trimIndent()
        val consumerOneSource = """
            import test.*
            import test.PersonService as Service

            package some.other.^package

            consume PersonEndpoint {
                uses Service { getNeighbors }
            }
        """.trimIndent()
        val consumerTwoSource = """
            package somewhere.else

            consume test.PersonEndpoint { uses test.PersonService }
        """.trimIndent()
        parseAndCheck(
            serviceSource to listOf(
                ExpectedMetadata("0:8" to "0:12", Meta(T.namespace)),
                ExpectedMetadata("2:5" to "2:8", Meta(T.enum, Mod.declaration)),
                ExpectedMetadata("2:11" to "2:19", Meta(T.enumMember, Mod.declaration)),
                ExpectedMetadata("2:21" to "2:26", Meta(T.enumMember, Mod.declaration)),
                ExpectedMetadata("2:28" to "2:34", Meta(T.enumMember, Mod.declaration)),
                ExpectedMetadata("4:7" to "4:13", Meta(T.`class`, Mod.declaration)),
                ExpectedMetadata("5:4" to "5:8", Meta(T.property, Mod.declaration)),
                ExpectedMetadata("5:10" to "5:14", Meta(T.type, Mod.defaultLibrary)),
                ExpectedMetadata("5:15" to "5:21", Meta(T.type, Mod.defaultLibrary)),
                ExpectedMetadata("5:24" to "5:28", Meta(T.function, Mod.defaultLibrary)),
                ExpectedMetadata("6:4" to "6:7", Meta(T.property, Mod.declaration)),
                ExpectedMetadata("6:9" to "6:12", Meta(T.enum)),
                ExpectedMetadata("9:8" to "9:21", Meta(T.`interface`, Mod.declaration)),
                ExpectedMetadata("10:10" to "10:22", Meta(T.method, Mod.declaration and Mod.async)),
                ExpectedMetadata("10:23" to "10:29", Meta(T.parameter, Mod.declaration)),
                ExpectedMetadata("10:31" to "10:37", Meta(T.`class`)),
                ExpectedMetadata("10:40" to "10:43", Meta(T.type, Mod.defaultLibrary)),
                ExpectedMetadata("10:44" to "10:50", Meta(T.type, Mod.defaultLibrary)),
                ExpectedMetadata("10:52" to "10:58", Meta(T.`class`)),
                ExpectedMetadata("11:11" to "11:26", Meta(T.method, Mod.declaration)),
            ),
            providerSource to listOf(
                ExpectedMetadata("0:8" to "0:12", Meta(T.namespace)),
                ExpectedMetadata("2:8" to "2:22", Meta(T.type, Mod.declaration)),
                ExpectedMetadata("3:15" to "3:28", Meta(T.`interface`)),
                ExpectedMetadata("3:31" to "3:43", Meta(T.method, Mod.async)),
                ExpectedMetadata("3:45" to "3:60", Meta(T.method)),
            ),
            consumerOneSource to listOf(
                ExpectedMetadata("0:7" to "0:11", Meta(T.namespace)),
                ExpectedMetadata("1:12" to "1:25", Meta(T.`interface`)),
                ExpectedMetadata("1:29" to "1:36", Meta(T.`interface`, Mod.declaration)),
                ExpectedMetadata("3:19" to "3:27", Meta(T.namespace)),
                ExpectedMetadata("5:8" to "5:22", Meta(T.type)),
                ExpectedMetadata("6:9" to "6:16", Meta(T.`interface`)),
                ExpectedMetadata("6:19" to "6:31", Meta(T.method, Mod.async)),
            ),
            consumerTwoSource to listOf(
                ExpectedMetadata("0:18" to "0:22", Meta(T.namespace)),
                ExpectedMetadata("2:13" to "2:27", Meta(T.type)),
                ExpectedMetadata("2:40" to "2:53", Meta(T.`interface`)),
            ),
        )
    }

    @Test
    fun `correctly tokenizes somewhat broken models`() {
        val serviceSource = """
            package broken

            record Person {
                name: Name
            }

            @Description("A enum for people")
            enum Person {
                foo,
                bar
            }

            service Person {
                async foo()
                oneway foo()
                foo(): Name
            }
        """.trimIndent()
        parseAndCheck(
            serviceSource to listOf(
                ExpectedMetadata("0:8" to "0:14", Meta(T.namespace)),
                ExpectedMetadata("2:7" to "2:13", Meta(T.`class`, Mod.declaration)),
                ExpectedMetadata("3:4" to "3:8", Meta(T.property, Mod.declaration)),
                ExpectedMetadata("3:10" to "3:14", Meta(T.type)),
                ExpectedMetadata("6:1" to "6:12", Meta(T.type, Mod.defaultLibrary)),
                ExpectedMetadata("7:5" to "7:11", Meta(T.enum, Mod.declaration)),
                ExpectedMetadata("8:4" to "8:7", Meta(T.enumMember, Mod.declaration)),
                ExpectedMetadata("9:4" to "9:7", Meta(T.enumMember, Mod.declaration)),
                ExpectedMetadata("12:8" to "12:14", Meta(T.`interface`, Mod.declaration)),
                ExpectedMetadata("13:10" to "13:13", Meta(T.method, Mod.declaration and Mod.async)),
                ExpectedMetadata("14:11" to "14:14", Meta(T.method, Mod.declaration)),
                ExpectedMetadata("15:4" to "15:7", Meta(T.method, Mod.declaration)),
                ExpectedMetadata("15:11" to "15:15", Meta(T.type)),
            ),
        )
    }

    private data class ExpectedMetadata(val range: Pair<String, String>, val metadata: Meta) {
        val testLocation = TestLocation(range)
    }

    private fun parseAndCheck(
        vararg sourceAndExpectedMessages: Pair<String, List<ExpectedMetadata>>,
    ) {
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val fileTree = sourceAndExpectedMessages.mapIndexed { index, (source) ->
            val filePath = URI("file:///tmp/SamtSemanticTokensTest-${index}.samt")
            val sourceFile = SourceFile(filePath, source)
            val parseContext = diagnosticController.getOrCreateContext(sourceFile)
            val stream = Lexer.scan(source.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val samtPackage = SemanticModelBuilder.build(fileTree, diagnosticController)

        for ((fileNode, expectedMetadata) in fileTree.zip(sourceAndExpectedMessages.map { it.second })) {
            val filePackage = samtPackage.resolveSubPackage(fileNode.packageDeclaration.name)
            val semanticTokens = SamtSemanticTokens.analyze(fileNode, filePackage)
            for (expected in expectedMetadata) {
                val actual = semanticTokens[expected.testLocation.getLocation(fileNode.sourceFile)]
                assertEquals(expected.metadata, actual, "Metadata for ${expected.range} did not match")
            }
        }
    }
}
