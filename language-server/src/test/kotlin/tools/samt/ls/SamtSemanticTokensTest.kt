package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel
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

    @Test
    fun `typealiases inherit token type`() {
        val source = """
            package typealiases
            
            typealias Builtin = String
            typealias Record = R
            typealias Enum = E
            typealias Service = S
            typealias Provider = P
            
            record R {
                b: Builtin
                e: Enum
            }

            enum E {}
            
            service S {
                get(): Record
            }

            provide P {
                implements Service
            
                transport http
            }
            
            consume Provider {
                uses Service
            }
        """.trimIndent()
        parseAndCheck(
            source to listOf(
                ExpectedMetadata("2:10" to "2:17", Meta(T.type, Mod.declaration)),
                ExpectedMetadata("3:10" to "3:16", Meta(T.`class`, Mod.declaration)),
                ExpectedMetadata("4:10" to "4:14", Meta(T.enum, Mod.declaration)),
                ExpectedMetadata("5:10" to "5:17", Meta(T.`interface`, Mod.declaration)),
                ExpectedMetadata("6:10" to "6:18", Meta(T.type, Mod.declaration)),
                ExpectedMetadata("9:7" to "9:14", Meta(T.type)),
                ExpectedMetadata("10:7" to "10:11", Meta(T.enum)),
                ExpectedMetadata("16:11" to "16:17", Meta(T.`class`)),
                ExpectedMetadata("20:15" to "20:22", Meta(T.`interface`)),
                ExpectedMetadata("25:8" to "25:16", Meta(T.type)),
                ExpectedMetadata("26:9" to "26:16", Meta(T.`interface`)),
            ),
        )
    }

    @Test
    fun `correctly tokenizes deprecations`() {
        val source = """
            package deprecations
            
            @Deprecated
            enum UserType {
                ADMIN, USER
            }
            
            @Deprecated
            typealias Id = Long(1..*)
            
            @Deprecated
            record User {
                id: Id
                @Deprecated
                type: UserType
            }
            
            @Deprecated
            service UserService {
                @Deprecated
                get(@Deprecated id: Id): User
            }
            
            provide UserProvider {
                implements UserService { get } 
            
                transport http
            }
        """.trimIndent()
        parseAndCheck(
            source to listOf(
                ExpectedMetadata("3:5" to "3:13", Meta(T.enum, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("8:10" to "8:12", Meta(T.type, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("11:7" to "11:11", Meta(T.`class`, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("12:8" to "12:10", Meta(T.type, Mod.deprecated)),
                ExpectedMetadata("14:4" to "14:8", Meta(T.property, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("14:10" to "14:18", Meta(T.enum, Mod.deprecated)),
                ExpectedMetadata("18:8" to "18:19", Meta(T.`interface`, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("20:4" to "20:7", Meta(T.method, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("20:20" to "20:22", Meta(T.parameter, Mod.declaration and Mod.deprecated)),
                ExpectedMetadata("20:24" to "20:26", Meta(T.type, Mod.deprecated)),
                ExpectedMetadata("20:29" to "20:33", Meta(T.`class`, Mod.deprecated)),
                ExpectedMetadata("24:15" to "24:26", Meta(T.`interface`, Mod.deprecated)),
                ExpectedMetadata("24:29" to "24:32", Meta(T.method, Mod.deprecated)),
            ),
        )
    }

    @Test
    fun `correctly tokenizes fully qualified names`() {
        val enumSource = """
            package test.lib
                        
            enum Enum {
                A, B
            }
        """.trimIndent()
        val recordSource = """
            package test.impl
            
            record Record {
                e: test.lib.Enum
            }
        """.trimIndent()
        parseAndCheck(
            enumSource to emptyList(),
            recordSource to listOf(
                ExpectedMetadata("3:7" to "3:11", Meta(T.namespace)),
                ExpectedMetadata("3:12" to "3:15", Meta(T.namespace)),
                ExpectedMetadata("3:16" to "3:20", Meta(T.enum))
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

        val semanticModel = SemanticModel.build(fileTree, diagnosticController)
        val samtPackage = semanticModel.global

        for ((fileNode, expectedMetadata) in fileTree.zip(sourceAndExpectedMessages.map { it.second })) {
            val filePackage = samtPackage.resolveSubPackage(fileNode.packageDeclaration.name)
            val semanticTokens = SamtSemanticTokens.analyze(fileNode, filePackage, semanticModel.userMetadata)
            for (expected in expectedMetadata) {
                val actual = semanticTokens[expected.testLocation.getLocation(fileNode.sourceFile)]
                assertEquals(expected.metadata, actual, "Metadata for ${expected.range} did not match")
            }
        }
    }
}
