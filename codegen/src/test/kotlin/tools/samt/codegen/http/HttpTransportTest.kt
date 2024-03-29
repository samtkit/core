package tools.samt.codegen.http

import tools.samt.api.plugin.ConfigurationElement
import tools.samt.api.plugin.ConfigurationObject
import tools.samt.api.plugin.TransportConfiguration
import tools.samt.api.plugin.TransportConfigurationParserParams
import tools.samt.api.transports.http.HttpMethod
import tools.samt.api.transports.http.SerializationMode
import tools.samt.api.transports.http.TransportMode
import tools.samt.codegen.PublicApiMapper
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel
import java.net.URI
import kotlin.test.*

class HttpTransportTest {
    private val diagnosticController = DiagnosticController(URI("file:///tmp"))

    @BeforeTest
    fun setup() {
        diagnosticController.contexts.clear()
        diagnosticController.globalMessages.clear()
    }

    @Test
    fun `default configuration return default values for operations`() {
        val config = HttpTransportConfigurationParser.parse(object : TransportConfigurationParserParams {
            override val config: ConfigurationObject? get() = null
            override fun reportInfo(message: String, context: ConfigurationElement?) {
                fail("Unexpected info message: $message")
            }

            override fun reportWarning(message: String, context: ConfigurationElement?) {
                fail("Unexpected warning message: $message")
            }

            override fun reportError(message: String, context: ConfigurationElement?) {
                fail("Unexpected error message: $message")
            }
        })
        assertEquals(SerializationMode.Json, config.serializationMode)
        assertEquals(emptyList(), config.services)
        assertEquals(HttpMethod.Post, config.getMethod("service", "operation"))
        assertEquals("", config.getServicePath("service"))
        assertEquals("/operation", config.getPath("service", "operation"))
        assertEquals("/operation", config.getFullPath("service", "operation"))
        assertEquals(
            TransportMode.Body,
            config.getTransportMode("service", "operation", "parameter")
        )
    }

    @Test
    fun `correctly parses complex example`() {
        val source = """
            package tools.samt.greeter

            typealias ID = String? (1..50)

            record Greeting {
                message: String (0..128)
            }

            enum GreetingType {
                HELLO,
                HI,
                HEY
            }

            service Greeter {
                greet(id: ID,
                      name: String (1..50),
                      type: GreetingType,
                      reference: Greeting
                ): Greeting
                greetAll(names: List<String?>): Map<String, Greeting>
                get(name: String)
                put()
                oneway delete()
                patch()
                default()
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Greeter: {
                            basePath: "/greeter",
                            greet: "POST /greet/{id} {name in header} {type in cookie}",
                            greetAll: "GET /greet/all {names  in queryParam}",
                            get: "GET /",
                            put: "PUT /",
                            delete: "DELETE /",
                            patch: "PATCH /"
                        }
                    }
                }
            }
        """.trimIndent()

        val transport = parseAndCheck(source to emptyList())
        assertIs<HttpTransportConfiguration>(transport)

        assertEquals(SerializationMode.Json, transport.serializationMode)
        assertEquals(listOf("Greeter"), transport.services.map { it.name })

        assertEquals(HttpMethod.Post, transport.getMethod("Greeter", "greet"))
        assertEquals("/greet/{id}", transport.getPath("Greeter", "greet"))
        assertEquals(
            TransportMode.Path,
            transport.getTransportMode("Greeter", "greet", "id")
        )
        assertEquals(
            TransportMode.Header,
            transport.getTransportMode("Greeter", "greet", "name")
        )
        assertEquals(
            TransportMode.Cookie,
            transport.getTransportMode("Greeter", "greet", "type")
        )
        assertEquals(
            TransportMode.Body,
            transport.getTransportMode("Greeter", "greet", "reference")
        )

        assertEquals("/greeter", transport.getServicePath("Greeter"))

        assertEquals(HttpMethod.Get, transport.getMethod("Greeter", "greetAll"))
        assertEquals("/greet/all", transport.getPath("Greeter", "greetAll"))
        assertEquals("/greeter/greet/all", transport.getFullPath("Greeter", "greetAll"))
        assertEquals(
            TransportMode.QueryParameter,
            transport.getTransportMode("Greeter", "greetAll", "names")
        )

        assertEquals(HttpMethod.Get, transport.getMethod("Greeter", "get"))
        assertEquals("/", transport.getPath("Greeter", "get"))
        assertEquals("/greeter/", transport.getFullPath("Greeter", "get"))
        assertEquals(
            TransportMode.QueryParameter,
            transport.getTransportMode("Greeter", "get", "name")
        )

        assertEquals(HttpMethod.Put, transport.getMethod("Greeter", "put"))
        assertEquals(HttpMethod.Delete, transport.getMethod("Greeter", "delete"))
        assertEquals(HttpMethod.Patch, transport.getMethod("Greeter", "patch"))
        assertEquals(HttpMethod.Post, transport.getMethod("Greeter", "default"))
        assertEquals("/default", transport.getPath("Greeter", "default"))
        assertEquals("/greeter/default", transport.getFullPath("Greeter", "default"))
    }

    @Test
    fun `fails for invalid HTTP method`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Greeter: {
                            greet: "YEET /greet"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(source to listOf("Error: Invalid http method 'YEET'"))
    }

    @Test
    fun `fails for invalid parameter binding`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
                foo()
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Greeter: {
                            greet: "POST /greet {name in yeet}",
                            foo: "POST /foo {name in header}"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(
            source to listOf(
                "Error: Invalid transport mode 'yeet'",
                "Error: Parameter 'name' not found in operation 'foo'"
            )
        )
    }

    @Test
    fun `fails for invalid path parameter binding`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
                foo()
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Greeter: {
                            greet: "POST /greet/{}/me",
                            foo: "POST /foo/{name}"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(
            source to listOf(
                "Error: Expected parameter name between curly braces in '/greet/{}/me'",
                "Error: Path parameter 'name' not found in operation 'foo'"
            )
        )
    }

    @Test
    fun `fails for invalid syntax`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Greeter: {
                            greet: "POST /greet {header:name}"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(source to listOf("Error: Invalid operation config for 'greet', expected '<method> <path> <parameters>'. A valid example: 'POST /greet {parameter1, parameter2 in queryParam}'"))
    }

    @Test
    fun `fails for non-existent service`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
            }

            service Foo {
                bar()
            }

            provide GreeterEndpoint {
                implements Greeter

                transport http {
                    operations: {
                        Foo: {
                            bar: "PUT /bar"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(source to listOf("Error: No service with name 'Foo' found in provider 'GreeterEndpoint'"))
    }

    @Test
    fun `fails for non-implemented operation`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
                bar()
            }

            provide GreeterEndpoint {
                implements Greeter { greet }

                transport http {
                    operations: {
                        Greeter: {
                            bar: "PUT /bar"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(source to listOf("Error: No operation with name 'bar' found in service 'Greeter' of provider 'GreeterEndpoint'"))
    }

    @Test
    fun `prevents body parameters in GET operations`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String, uuid: String): String
            }

            provide GreeterEndpoint {
                implements Greeter { greet }

                transport http {
                    operations: {
                        Greeter: {
                            greet: "GET /greet {name in body} {uuid in header}"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(
            source to listOf(
                "Error: HTTP GET method doesn't accept 'name' as a BODY parameter"
            )
        )
    }


    @Test
    fun `cannot map two operations to the same method path combination`() {
        val source = """
            package tools.samt.greeter

            service Greeter {
                greet(name: String): String
                greetTwo(name1: String, name2: String): String
                greetThree(name: String): String
                greetFour(name: String): String
            }

            provide GreeterEndpoint {
                implements Greeter { greet, greetTwo, greetThree, greetFour }

                transport http {
                    operations: {
                        Greeter: {
                            greet: "GET /greet",
                            greetTwo: "GET /greet",
                            greetThree: "POST /greet/{name}",
                            greetFour: "POST /greet/{name}"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(
            source to listOf(
                "Error: Operation 'Greeter.greetTwo' cannot be mapped to the same method and path combination (GET /greet) as operation 'Greeter.greet'",
                "Error: Operation 'Greeter.greetFour' cannot be mapped to the same method and path combination (POST /greet/{name}) as operation 'Greeter.greetThree'"
            )
        )
    }

    @Test
    fun `operations within different services cannot have the same explicit path mapping`() {
        val source = """
            package tools.samt.greeter

            service HelloSayer {
                say(name: String): String
            }

            service GoodbyeSayer {
                say(name: String): String
            }

            provide SayerEndpoint {
                implements HelloSayer { say }
                implements GoodbyeSayer { say }

                transport http {
                    operations: {
                        HelloSayer: {
                            basePath: "/sayer",
                            say: "GET /say"
                        },
                        GoodbyeSayer: {
                            basePath: "/sayer",
                            say: "GET /say"
                        }
                    }
                }
            }
        """.trimIndent()

        parseAndCheck(
            source to listOf(
                "Error: Operation 'GoodbyeSayer.say' cannot be mapped to the same method and path combination (GET /sayer/say) as operation 'HelloSayer.say'",
            )
        )
    }

    private fun parseAndCheck(
        vararg sourceAndExpectedMessages: Pair<String, List<String>>,
    ): TransportConfiguration {
        val fileTree = sourceAndExpectedMessages.mapIndexed { index, (source) ->
            val filePath = URI("file:///tmp/HttpTransportTest-${index}.samt")
            val sourceFile = SourceFile(filePath, source)
            val parseContext = diagnosticController.getOrCreateContext(sourceFile)
            val stream = Lexer.scan(source.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val parseMessageCount = diagnosticController.contexts.associate { it.source.content to it.messages.size }

        val semanticModel = SemanticModel.build(fileTree, diagnosticController)

        val publicApiMapper = PublicApiMapper(listOf(HttpTransportConfigurationParser), diagnosticController)

        val transport =
            semanticModel.global.allSubPackages.map { publicApiMapper.toPublicApi(it) }.flatMap { it.providers }
                .single().transport

        for ((source, expectedMessages) in sourceAndExpectedMessages) {
            val messages = diagnosticController.contexts
                .first { it.source.content == source }
                .messages
                .drop(parseMessageCount.getValue(source))
                .map { "${it.severity}: ${it.message}" }
            assertEquals(expectedMessages, messages)
        }

        return transport
    }
}
