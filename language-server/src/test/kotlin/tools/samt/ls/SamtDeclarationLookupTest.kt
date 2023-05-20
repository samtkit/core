package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.*
import tools.samt.semantic.SemanticModel
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SamtDeclarationLookupTest {
    @Test
    fun `correctly find definition in complex model`() {
        val serviceSource = """
            package test
            
            enum Friendliness {
                FRIENDLY,
                NEUTRAL,
                HOSTILE
            }

            record Person {
                name: List<String? (size(1..100))>
                age: Int
                friendliness: Friendliness
            }

            service PersonService {
                async getNeighbors(person: Person): Map<String, Person> (*..100)
            }
        """.trimIndent()
        val providerSource = """
            package test

            provide PersonEndpoint {
                implements PersonService { getNeighbors }

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
                ExpectedDefinition("11:18" to "11:30") { it is EnumDeclarationNode && it.name.name == "Friendliness" },
                ExpectedDefinition("15:31" to "15:37") { it is RecordDeclarationNode && it.name.name == "Person" },
                ExpectedDefinition("15:52" to "15:58") { it is RecordDeclarationNode && it.name.name == "Person" },
            ),
            providerSource to listOf(
                ExpectedDefinition("3:15" to "3:28") { it is ServiceDeclarationNode && it.name.name == "PersonService" },
                ExpectedDefinition("3:31" to "3:43") { it is OperationNode && it.name.name == "getNeighbors" },
            ),
            consumerOneSource to listOf(
                ExpectedDefinition("1:12" to "1:25") { it is ServiceDeclarationNode && it.name.name == "PersonService" },
                ExpectedDefinition("5:8" to "5:22") { it is ProviderDeclarationNode && it.name.name == "PersonEndpoint" },
                ExpectedDefinition("6:9" to "6:16") { it is ServiceDeclarationNode && it.name.name == "PersonService" },
                ExpectedDefinition("6:19" to "6:31") { it is OperationNode && it.name.name == "getNeighbors" },
            ),
            consumerTwoSource to listOf(
                ExpectedDefinition("2:13" to "2:27") { it is ProviderDeclarationNode && it.name.name == "PersonEndpoint" },
                ExpectedDefinition("2:40" to "2:53") { it is ServiceDeclarationNode && it.name.name == "PersonService" },
            ),
        )
    }

    @Test
    fun `finds definition for name of the user defined types themselves`() {
        val serviceSource = """
            package test
            
            enum Friendliness {
                FRIENDLY,
                NEUTRAL,
                HOSTILE
            }

            record Person {
                name: List<String? (size(1..100))>
                age: Int
            }

            service PersonService {
                foo()
            }
        """.trimIndent()
        val providerSource = """
            package test

            provide PersonEndpoint {
                implements PersonService

                transport HTTP
            }
        """.trimIndent()
        parseAndCheck(
            serviceSource to listOf(
                ExpectedDefinition("2:5" to "2:17") { it is EnumDeclarationNode && it.name.name == "Friendliness" },
                ExpectedDefinition("8:7" to "8:13") { it is RecordDeclarationNode && it.name.name == "Person" },
                ExpectedDefinition("13:8" to "13:21") { it is ServiceDeclarationNode && it.name.name == "PersonService" },
                ExpectedDefinition("14:4" to "14:7") { it is OperationNode && it.name.name == "foo" },
            ),
            providerSource to listOf(
                ExpectedDefinition("2:8" to "2:22") { it is ProviderDeclarationNode && it.name.name == "PersonEndpoint" },
            ),
        )
    }

    private data class ExpectedDefinition(val range: Pair<String, String>, val matcher: (definition: Node) -> Boolean) {
        val testLocation = TestLocation(range)
    }

    private fun parseAndCheck(
        vararg sourceAndExpectedMessages: Pair<String, List<ExpectedDefinition>>,
    ) {
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val fileTree = sourceAndExpectedMessages.mapIndexed { index, (source) ->
            val filePath = URI("file:///tmp/SamtDeclarationLookupTest-${index}.samt")
            val sourceFile = SourceFile(filePath, source)
            val parseContext = diagnosticController.getOrCreateContext(sourceFile)
            val stream = Lexer.scan(source.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val samtPackage = SemanticModel.build(fileTree, diagnosticController).global

        for ((fileNode, expectedMetadata) in fileTree.zip(sourceAndExpectedMessages.map { it.second })) {
            val filePackage = samtPackage.resolveSubPackage(fileNode.packageDeclaration.name)
            val definitionLookup = SamtDeclarationLookup.analyze(fileNode, filePackage)
            for (expected in expectedMetadata) {
                val actual = definitionLookup[expected.testLocation.getLocation(fileNode.sourceFile)]
                assertNotNull(actual, "No definition found for ${expected.range}")
                assertTrue(expected.matcher(actual.declaration), "Matcher for ${expected.range} did not match")
            }
        }
    }
}
