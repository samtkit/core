package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.Package
import tools.samt.semantic.SemanticModelBuilder
import java.net.URI
import kotlin.test.*

class SamtReferencesLookupTest {
    @Test
    fun `correctly find references in complex model`() {
        val serviceSource = """
            package test
            
            enum Friendliness {
                FRIENDLY,
                NEUTRAL,
                HOSTILE
            }

            record Person {
                friendliness: Friendliness
            }

            service PersonService {
                getNeighbors(person: Person): Map<String, Person?>? (*..100)
            }
        """.trimIndent().let { SourceFile(URI("file:///tmp/SamtSemanticTokensTest-serviceSource.samt"), it) }
        val providerSource = """
            package test

            provide PersonEndpoint {
                implements PersonService { getNeighbors }

                transport HTTP
            }
        """.trimIndent().let { SourceFile(URI("file:///tmp/SamtSemanticTokensTest-providerSource.samt"), it) }
        val consumerOneSource = """
            import test.*
            import test.PersonService as Service

            package some.other.^package

            consume PersonEndpoint {
                uses Service { getNeighbors }
            }
        """.trimIndent().let { SourceFile(URI("file:///tmp/SamtSemanticTokensTest-consumerOneSource.samt"), it) }
        val consumerTwoSource = """
            package somewhere.else

            consume test.PersonEndpoint { uses test.PersonService }
        """.trimIndent().let { SourceFile(URI("file:///tmp/SamtSemanticTokensTest-consumerTwoSource.samt"), it) }
        val (samtPackage, referencesLookup) = parse(serviceSource, providerSource, consumerOneSource, consumerTwoSource)

        val testPackage = samtPackage.subPackages.single { it.name == "test" }
        val friendliness = testPackage.enums.single { it.name == "Friendliness" }
        val person = testPackage.records.single { it.name == "Person" }
        val personService = testPackage.services.single { it.name == "PersonService" }
        val personEndpoint = testPackage.providers.single { it.name == "PersonEndpoint" }
        val getNeighbors = personService.operations.single { it.name == "getNeighbors" }

        val friendlinessReferences = referencesLookup[friendliness]
        assertNotNull(friendlinessReferences)
        assertEquals(1, friendlinessReferences.size, "Following list had unexpected amount of entries: $friendlinessReferences")
        assertContains(friendlinessReferences, TestLocation("9:18" to "9:30").getLocation(serviceSource))

        val personReferences = referencesLookup[person]
        assertNotNull(personReferences)
        assertEquals(2, personReferences.size, "Following list had unexpected amount of entries: $personReferences")
        assertContains(personReferences, TestLocation("13:25" to "13:31").getLocation(serviceSource))
        assertContains(personReferences, TestLocation("13:46" to "13:52").getLocation(serviceSource))

        val personServiceReferences = referencesLookup[personService]
        assertNotNull(personServiceReferences)
        assertEquals(4, personServiceReferences.size, "Following list had unexpected amount of entries: $personServiceReferences")
        assertContains(personServiceReferences, TestLocation("3:15" to "3:28").getLocation(providerSource))
        assertContains(personServiceReferences, TestLocation("1:12" to "1:25").getLocation(consumerOneSource))
        assertContains(personServiceReferences, TestLocation("6:9" to "6:16").getLocation(consumerOneSource))
        assertContains(personServiceReferences, TestLocation("2:40" to "2:53").getLocation(consumerTwoSource))

        val personEndpointReferences = referencesLookup[personEndpoint]
        assertNotNull(personEndpointReferences)
        assertEquals(2, personEndpointReferences.size, "Following list had unexpected amount of entries: $personEndpointReferences")
        assertContains(personEndpointReferences, TestLocation("5:8" to "5:22").getLocation(consumerOneSource))
        assertContains(personEndpointReferences, TestLocation("2:13" to "2:27").getLocation(consumerTwoSource))

        val getNeighborsReferences = referencesLookup[getNeighbors]
        assertNotNull(getNeighborsReferences)
        assertEquals(2, getNeighborsReferences.size, "Following list had unexpected amount of entries: $getNeighborsReferences")
        assertContains(getNeighborsReferences, TestLocation("3:31" to "3:43").getLocation(providerSource))
        assertContains(getNeighborsReferences, TestLocation("6:19" to "6:31").getLocation(consumerOneSource))
    }

    private fun parse(vararg sourceAndExpectedMessages: SourceFile): Pair<Package, SamtReferencesLookup> {
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val fileTree = sourceAndExpectedMessages.map { sourceFile ->
            val parseContext = diagnosticController.getOrCreateContext(sourceFile)
            val stream = Lexer.scan(sourceFile.content.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val samtPackage = SemanticModelBuilder.build(fileTree, diagnosticController)

        val filesAndPackages = fileTree.map { it to samtPackage.resolveSubPackage(it.packageDeclaration.name) }
        return Pair(samtPackage, SamtReferencesLookup.analyze(filesAndPackages))
    }
}
