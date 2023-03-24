package tools.samt.parser

import org.junit.jupiter.api.Nested
import tools.samt.lexer.Lexer
import org.junit.jupiter.api.assertThrows
import tools.samt.common.*
import kotlin.test.*

class ParserUnitTest {

    @Nested
    inner class PackageTest {
        @Test
        fun `no package declaration`() {
            val source = """
                record A {}
            """.trimIndent()
            val exception = parseWithFatalError(source)
            assertEquals("Files must have at least one package declaration", exception.message)
        }

        @Test
        fun `single package declaration`() {
            val source = """
                package tools.samt.parser.foo
            """
            val fileTree = parse(source)
            assertPackage("tools.samt.parser.foo", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertEmpty(fileTree.statements)
        }

        @Test
        fun `multiple package declarations`() {
            val source = """
                package a
                package b
            """.trimIndent()
            val (fileTree, diagnostics) = parseWithRecoverableError(source)

            val firstPackageLocation = Location(
                FileOffset(0, 0, 0),
                FileOffset(8, 0, 8),
            )
            val secondPackageLocation = Location(
                FileOffset(10, 1, 0),
                FileOffset(18, 1, 8),
            )

            assertEquals(
                listOf(
                    DiagnosticMessage(
                        "Cannot have multiple package declarations per file",
                        secondPackageLocation,
                        DiagnosticSeverity.Error
                    ),
                    DiagnosticMessage("Previously declared package here", firstPackageLocation, DiagnosticSeverity.Info),
                ), diagnostics.messages
            )
            assertPackage("b", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertEmpty(fileTree.statements)
        }

        @Test
        fun `record must be after package`() {
            val source = """
                record Foo

                package recordBeforePackage
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Expected a package declaration before any other statements",
                diagnostics.messages.single().message
            )
            assertPackage("recordBeforePackage", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                record("Foo")
            }
        }
    }

    @Nested
    inner class ImportTest {
        @Test
        fun `simple and wildcard imports`() {
            val source = """
                import tools.samt.*
                import library.foo.bar.Baz
                import library.foo.bar.Baz as BazAlias

                package imports
            """
            val fileTree = parse(source)
            assertPackage("imports", fileTree.packageDeclaration)
            assertNodes(fileTree.imports) {
                wildcardImport("tools.samt")
                typeImport("library.foo.bar.Baz")
                typeImport("library.foo.bar.Baz", "BazAlias")
            }
            assertEmpty(fileTree.statements)
        }

        @Test
        fun `illegal wildcard import with alias`() {
            val source = """
                import tools.samt.* as Samt

                package badImports
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Wildcard imports cannot have an alias",
                diagnostics.messages.single().message
            )
            assertPackage("badImports", fileTree.packageDeclaration)
            assertNodes(fileTree.imports) {
                wildcardImport("tools.samt")
            }
            assertEmpty(fileTree.statements)
        }

        @Test
        fun `imports must be before package`() {
            val source = """
                import tools.samt.*

                package badImports

                import library.foo.bar.Baz
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Import statements must be placed before the package declaration",
                diagnostics.messages.single().message
            )
            assertPackage("badImports", fileTree.packageDeclaration)
            assertNodes(fileTree.imports) {
                wildcardImport("tools.samt")
                typeImport("library.foo.bar.Baz")
            }
            assertEmpty(fileTree.statements)
        }
    }

    @Nested
    inner class TypeDeclarations {
        @Test
        fun `empty record`() {
            val source = """
            package emptyRecord

            record A {}
        """
            val fileTree = parse(source)
            assertPackage("emptyRecord", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                record("A")
            }
        }

        @Test
        fun `empty record without braces`() {
            val source = """
            package emptyRecord

            record A
            record B extends com.test.C
        """
            val fileTree = parse(source)
            assertPackage("emptyRecord", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                record("A")
                record("B", listOf("com.test.C"))
            }
        }

        @Test
        fun `complex record with constraints and annotations`() {
            val source = """
            package complexRecord

            record Person {
                password: String ( size(16..100) )
                name: String ( size(*..256), pattern("A-Za-z", true) )?
                age: Integer? ( range(18..*) )
            }
        """
            val fileTree = parse(source)
            assertPackage("complexRecord", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                record("Person") {
                    field("password") {
                        callExpression({ bundleIdentifier("String") }) {
                            callExpression({ bundleIdentifier("size") }) {
                                rangeExpression(
                                    { integer(16) },
                                    { integer(100) }
                                )
                            }
                        }
                    }
                    field("name") {
                        optional {
                            callExpression({ bundleIdentifier("String") }) {
                                callExpression({ bundleIdentifier("size") }) {
                                    rangeExpression(
                                        { wildcard() },
                                        { integer(256) }
                                    )
                                }
                                callExpression({ bundleIdentifier("pattern") }) {
                                    string("A-Za-z")
                                    boolean(true)
                                }
                            }
                        }
                    }
                    field("age") {
                        callExpression({ optional { bundleIdentifier("Integer") } }) {
                            callExpression({ bundleIdentifier("range") }) {
                                rangeExpression(
                                    { integer(18) },
                                    { wildcard() }
                                )
                            }
                        }
                    }
                }
            }
        }

        @Test
        fun `type aliases`() {
            val source = """
                package aliases

                alias A: String? ( foo(1..2.3..3) )
                alias B: List<A?>?
                alias C: Map<String, Integer> ( uniqueKeys((false)) )
            """
            val fileTree = parse(source)
            assertPackage("aliases", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                alias("A") {
                    callExpression({ optional { bundleIdentifier("String") } }) {
                        callExpression({ bundleIdentifier("foo") }) {
                            rangeExpression(
                                { integer(1) },
                                { rangeExpression({ float(2.3) }, { integer(3) }) },
                            )
                        }
                    }
                }
                alias("B") {
                    optional {
                        genericSpecialization({ bundleIdentifier("List") }) {
                            optional { bundleIdentifier("A") }
                        }
                    }
                }
                alias("C") {
                    callExpression({
                        genericSpecialization({ bundleIdentifier("Map") }) {
                            bundleIdentifier("String")
                            bundleIdentifier("Integer")
                        }
                    }) {
                        callExpression({ bundleIdentifier("uniqueKeys") }) {
                            boolean(false)
                        }
                    }
                }
            }
        }

        @Test
        fun `optional generic declarations`() {
            val source = """
                package aliases

                alias A: String?
                alias B: List<A?>?
            """
            val fileTree = parse(source)
            assertPackage("aliases", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                alias("A") {
                    optional {
                        bundleIdentifier("String")
                    }
                }
                alias("B") {
                    optional {
                        genericSpecialization({ bundleIdentifier("List") }) {
                            optional { bundleIdentifier("A") }
                        }
                    }
                }
            }
        }

        @Test
        fun `illegal generics without arguments`() {
            val source = """
                package illegalAliases

                alias A: List<>
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Generic specialization requires at least one argument",
                diagnostics.messages.single().message
            )
            assertPackage("illegalAliases", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                alias("A") {
                    genericSpecialization({ bundleIdentifier("List") })
                }
            }
        }

        @Test
        fun `enum declaration`() {
            val source = """
                package ^enum

                enum Foo {
                    A, B,
                    C,
                    D
                }
            """
            val fileTree = parse(source)
            assertPackage("enum", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                enum("Foo", expectedValues = listOf("A", "B", "C", "D"))
            }
        }
    }

    @Nested
    inner class ServiceTest {
        @Test
        fun `illegal oneway with raises type`() {
            val source = """
                package ^oneway
                service Foo {
                    oneway A() raises Fault
                }
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Oneway operations cannot raise exceptions",
                diagnostics.messages.single().message
            )
            assertPackage("oneway", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                service("Foo") {
                    onewayOperation("A")
                }
            }
        }

        @Test
        fun `service with multiple parameters`() {
            val source = """
                package parameters
                service Foo {
                    A(
                        id: Id,
                        name: String ( size(1..*), encoding("UTF-8") )
                    )
                }
            """
            val fileTree = parse(source)
            assertPackage("parameters", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                service("Foo") {
                    requestReplyOperation("A", hasReturnType = false, expectedParameters = {
                        parameter("id") { bundleIdentifier("Id") }
                        parameter("name") {
                            callExpression({ bundleIdentifier("String") }) {
                                callExpression({ bundleIdentifier("size") }) {
                                    rangeExpression({ integer(1) }, { wildcard() })
                                }
                                callExpression({ bundleIdentifier("encoding") }) {
                                    string("UTF-8")
                                }
                            }
                        }
                    })
                }
            }
        }

        @Test
        fun `service with oneway operations`() {
            val source = """
                package ^oneway
                service Foo {
                    oneway A(id: Id?)
                }
            """
            val fileTree = parse(source)
            assertPackage("oneway", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                service("Foo") {
                    onewayOperation("A") {
                        parameter("id") { optional { bundleIdentifier("Id") } }
                    }
                }
            }
        }

        @Test
        fun `illegal oneway with return type`() {
            val source = """
                package ^oneway
                service Foo {
                    oneway A(): Foo
                }
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Oneway operations cannot have a return type",
                diagnostics.messages.single().message
            )
            assertPackage("oneway", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                service("Foo") {
                    onewayOperation("A")
                }
            }
        }

        @Test
        fun `service with RequestReply operations`() {
            val source = """
                package RequestReply
                service Foo {
                    A(): String raises Foo, Bar
                    B() raises Foo
                    async C(): Integer ( range(1..2) )
                }
            """
            val fileTree = parse(source)
            assertPackage("RequestReply", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                service("Foo") {
                    requestReplyOperation("A", expectedRaises = listOf("Foo", "Bar")) {
                        bundleIdentifier("String")
                    }
                    requestReplyOperation("B", expectedRaises = listOf("Foo"), hasReturnType = false)
                    requestReplyOperation("C", expectedIsAsync = true) {
                        callExpression({ bundleIdentifier("Integer") }) {
                            callExpression({ bundleIdentifier("range") }) {
                                rangeExpression({ integer(1) }, { integer(2) })
                            }
                        }
                    }
                }
            }
        }
    }

    @Nested
    inner class MalformedSyntaxTest {
        @Test
        fun `unexpected end of file`() {
            val source = """
                package a
                
                alias A: List<B
            """
            val exception = parseWithFatalError(source)
            assertEquals("Expected '>' but reached end of file", exception.message)
        }

        @Test
        fun `unexpected structure token`() {
            val source = """
                package a
                
                alias A: List<B}
            """
            val exception = parseWithFatalError(source)
            assertEquals("Expected '>' but got '}'", exception.message)
        }

        @Test
        fun `illegal keyword`() {
            val source = """
                import package foo
            """
            val exception = parseWithFatalError(source)
            assertEquals("'package' is a reserved keyword, did you mean to escape it? (e.g. '^package')", exception.message)
        }

        @Test
        fun `illegal keyword 2`() {
            val source = """
                package foo

                record record A {}
            """
            val exception = parseWithFatalError(source)
            assertEquals("'record' is a reserved keyword, did you mean to escape it? (e.g. '^record')", exception.message)
        }

        @Test
        fun `unexpected declaration token`() {
            val source = """
                package foo

                uses Foo {}
            """
            val exception = parseWithFatalError(source)
            assertEquals("Expected some sort of a declaration", exception.message)
        }
    }

    @Nested
    inner class ProviderTest {
        @Test
        fun `provider can implement multiple services`() {
            val source = """
                package ^provide

                provide FooEndpoint {
                    implements FooService { foo, foo2 }
                    implements BarService

                    transport HTTP {
                        port: 8080
                    }
                }
            """
            val fileTree = parse(source)
            assertPackage("provide", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                provider(
                    "FooEndpoint",
                    expectedImplements = { implements("FooService", "foo", "foo2"); implements("BarService") },
                    expectedTransport = { transport("HTTP") { objectLiteral { field("port") { integer(8080) } } } },
                )
            }
        }

        @Test
        fun `provider given illegal statement`() {
            val source = """
                package illegalProvider

                provide FooEndpoint {
                    record Foo {}
                }
            """
            val error = parseWithFatalError(source)
            assertEquals("Expected 'implements' or 'transport' but found 'record'", error.message)
        }

        @Test
        fun `provider missing a transport declaration`() {
            val source = """
                package illegalProvider

                provide FooEndpoint {
                
                }
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Provider is missing a transport declaration",
                diagnostics.messages.single().message
            )
        }

        @Test
        fun `provider given multiple transport declarations`() {
            val source = """
                package illegalProvider

                provide FooEndpoint {
                    transport http
                    transport rest
                }
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                listOf(
                    "Provider can only have one transport declaration",
                    "Previously declared here"
                ), diagnostics.messages.map { it.message }
            )
        }

        @Test
        fun `provider implementation without operations is invalid`() {
            val source = """
                package illegalProvider

                provide BarEndpoint {
                    implements BarService { }
                    transport HTTP
                }
            """
            val (fileTree, diagnostics) = parseWithRecoverableError(source)
            assertEquals(
                "Expected at least one operation name in the implements clause",
                diagnostics.messages.single().message
            )
            assertPackage("illegalProvider", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                provider(
                    "BarEndpoint",
                    expectedImplements = { implements("BarService") },
                    expectedTransport = { transport("HTTP", hasConfiguration = false) },
                )
            }
        }
    }

    @Nested
    inner class ConsumerTest {
        @Test
        fun `consume multiple services`() {
            val source = """
                package consumer

                consume tools.samt.example.FooEndpoint {
                    uses FooService { foo, foo2 }
                    uses BarService
                }
            """
            val fileTree = parse(source)
            assertPackage("consumer", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                consumer(
                    "tools.samt.example.FooEndpoint",
                ) {
                    uses("FooService", "foo", "foo2")
                    uses("BarService")
                }
            }
        }
    }

    @Nested
    inner class AnnotationTest {
        @Test
        fun `annotations on all possible constructs`() {
            val source = """
                package annotations

                @Password
                @Encrypted
                alias Password: String
                
                @Foo
                @Bar
                @Baz
                enum Foo {
                    A,
                    B,
                    C,
                    D
                }

                @Author("Foo", "Bar")
                @Description("This is a record")
                record Person {
                    @Secret
                    password: Password
                }

                @Version(1, 0, 0)
                service PersonService {
                    @Get()
                    getPerson(@Id id: Integer): Person
                    @Post([true, false, { foo: "bar" }])
                    oneway postPerson()
                }
            """
            val fileTree = parse(source)
            assertPackage("annotations", fileTree.packageDeclaration)
            assertEmpty(fileTree.imports)
            assertNodes(fileTree.statements) {
                alias(
                    expectedName = "Password",
                    expectedAnnotations = {
                        annotation("Password")
                        annotation("Encrypted")
                    },
                    expectedType = { bundleIdentifier("String") },
                )
                enum(
                    expectedName = "Foo",
                    expectedAnnotations = {
                        annotation("Foo")
                        annotation("Bar")
                        annotation("Baz")
                    },
                    expectedValues = listOf("A", "B", "C", "D")
                )
                record(
                    expectedName = "Person",
                    expectedAnnotations = {
                        annotation("Author") { string("Foo"); string("Bar") }
                        annotation("Description") { string("This is a record") }
                    },
                    expectedFields = {
                        field(
                            expectedName = "password",
                            expectedAnnotations = {
                                annotation("Secret")
                            },
                            expectedType = {
                                bundleIdentifier("Password")
                            },
                        )
                    })
                service(
                    expectedName = "PersonService",
                    expectedAnnotations = {
                        annotation("Version") {
                            integer(1); integer(0); integer(0)
                        }
                    },
                    expectedOperations = {
                        requestReplyOperation(
                            expectedName = "getPerson",
                            expectedAnnotations = {
                                annotation("Get")
                            },
                            expectedParameters = {
                                parameter(
                                    expectedName = "id",
                                    expectedAnnotations = {
                                        annotation("Id")
                                    },
                                    expectedType = {
                                        bundleIdentifier("Integer")
                                    },
                                )
                            },
                            expectedReturnType = {
                                bundleIdentifier("Person")
                            },
                        )
                        onewayOperation(
                            expectedName = "postPerson",
                            expectedAnnotations = {
                                annotation("Post") {
                                    array {
                                        boolean(true)
                                        boolean(false)
                                        objectLiteral {
                                            field("foo") {
                                                string("bar")
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    },
                )
            }
        }

        @Test
        fun `illegal annotations on all possible constructs`() {
            val source = """
                @NotAllowed
                package annotations
            """
            val exception = parseWithFatalError(source)
            assertEquals("Expected declaration with annotation support", exception.message)
        }
    }

    private fun parse(source: String): FileNode {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val fileTree = Parser.parse(stream, diagnostics)
        diagnostics.messages.forEach { println(it) }
        assertFalse(diagnostics.hasErrors(), "Expected no errors, but had errors")
        return fileTree
    }

    private fun parseWithRecoverableError(source: String): Pair<FileNode, DiagnosticConsole> {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val fileTree = Parser.parse(stream, diagnostics)
        diagnostics.messages.forEach { println(it) }
        assertTrue(diagnostics.hasErrors(), "Expected errors, but had no errors")
        return Pair(fileTree, diagnostics)
    }

    private fun parseWithFatalError(source: String): ParserException {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val ex = assertThrows<ParserException> { Parser.parse(stream, diagnostics) }
        diagnostics.messages.forEach { println(it) }
        assertTrue(diagnostics.hasErrors(), "Expected errors, but had no errors")
        return ex
    }
}
