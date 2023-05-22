package tools.samt.codegen.kotlin.ktor

import tools.samt.codegen.*
import tools.samt.codegen.http.HttpTransportConfiguration
import tools.samt.codegen.kotlin.GeneratedFilePreamble
import tools.samt.codegen.kotlin.KotlinTypesGenerator
import tools.samt.codegen.kotlin.getQualifiedName

object KotlinKtorConsumerGenerator : Generator {
    override val name: String = "kotlin-ktor-consumer"

    override fun generate(generatorParams: GeneratorParams): List<CodegenFile> {
        generatorParams.packages.forEach {
            generateMappings(it, generatorParams.options)
            generatePackage(it, generatorParams.options)
        }
        val result = KotlinTypesGenerator.generate(generatorParams) + emittedFiles
        emittedFiles.clear()
        return result
    }

    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generateMappings(pack: SamtPackage, options: Map<String, String>) {
        val packageSource = mappingFileContent(pack, options)
        if (packageSource.isNotEmpty()) {
            val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/KtorMappings.kt"
            val file = CodegenFile(filePath, packageSource)
            emittedFiles.add(file)
        }
    }

    private fun generatePackage(pack: SamtPackage, options: Map<String, String>) {
        val relevantConsumers = pack.consumers.filter { it.provider.transport is HttpTransportConfiguration }
        if (relevantConsumers.isNotEmpty()) {
            // generate ktor consumers
            relevantConsumers.forEach { consumer ->
                val transportConfiguration = consumer.provider.transport as HttpTransportConfiguration

                val packageSource = buildString {
                    appendLine(GeneratedFilePreamble)
                    appendLine()
                    appendLine("package ${pack.getQualifiedName(options)}")
                    appendLine()

                    appendConsumer(consumer, transportConfiguration, options)
                }

                val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/Consumer.kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }
        }
    }

    data class ConsumerInfo(val uses: ConsumerUses) {
        val reference = uses.service
        val service = reference.type as ServiceType
        val serviceArgumentName = service.name.replaceFirstChar { it.lowercase() }
    }

    private fun StringBuilder.appendConsumer(consumer: ConsumerType, transportConfiguration: HttpTransportConfiguration, options: Map<String, String>) {
        appendLine("import io.ktor.client.*")
        appendLine("import io.ktor.client.engine.cio.*")
        appendLine("import io.ktor.client.plugins.contentnegotiation.*")
        appendLine("import io.ktor.client.request.*")
        appendLine("import io.ktor.client.statement.*")
        appendLine("import io.ktor.http.*")
        appendLine("import io.ktor.serialization.kotlinx.json.*")
        appendLine("import io.ktor.util.*")
        appendLine("import kotlinx.coroutines.runBlocking")
        appendLine("import kotlinx.serialization.json.*")

        val implementedServices = consumer.uses.map { ConsumerInfo(it) }
        appendLine("// ${transportConfiguration.exceptionMap}")
        appendLine("class ${consumer.provider.name}Impl(private val `consumer baseUrl`: String) : ${implementedServices.joinToString { it.service.getQualifiedName(options) }} {")
        implementedServices.forEach { info ->
            appendConsumerOperations(info, transportConfiguration, options)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendConsumerOperations(info: ConsumerInfo, transportConfiguration: HttpTransportConfiguration, options: Map<String, String>) {
        appendLine("    private val client = HttpClient(CIO) {")
        appendLine("        install(ContentNegotiation) {")
        appendLine("            json()")
        appendLine("        }")
        appendLine("    }")
        appendLine()

        val service = info.service
        info.uses.operations.forEach { operation ->
            val operationParameters = operation.parameters.joinToString { "${it.name}: ${it.type.getQualifiedName(options)}" }

            when (operation) {
                is RequestResponseOperation -> {
                    if (operation.returnType != null) {
                        appendLine("    override fun ${operation.name}($operationParameters): ${operation.returnType!!.getQualifiedName(options)} {")
                    } else {
                        appendLine("    override fun ${operation.name}($operationParameters): Unit {")
                    }

                    // TODO Config: HTTP status code
                    // TODO serialize response correctly
                    // TODO validate response
                    appendLine("return runBlocking {")

                    appendConsumerServiceCall(info, operation, transportConfiguration)
                    appendConsumerResponseParsing(operation, transportConfiguration)

                    appendLine("}")
                }

                is OnewayOperation -> {
                    // TODO
                }
            }
        }
    }

    private fun StringBuilder.appendConsumerServiceCall(info: ConsumerInfo, operation: ServiceOperation, transport: HttpTransportConfiguration) {
        /*
            val response = client.request("$baseUrl/todos/$title") {
                method = HttpMethod.Post
                headers["title"] = title
                cookie("description", description)
                setBody(
                    buildJsonObject {
                        put("title", title)
                        put("description", description)
                    }
                )
                contentType(ContentType.Application.Json)
            }
        */

        // collect parameters for each transport type
        val headerParameters = mutableListOf<String>()
        val cookieParameters = mutableListOf<String>()
        val bodyParameters = mutableListOf<String>()
        val pathParameters = mutableListOf<String>()
        val queryParameters = mutableListOf<String>()
        operation.parameters.forEach {
            val name = it.name
            val transportMode = transport.getTransportMode(info.service.name, operation.name, name)
            when (transportMode) {
                HttpTransportConfiguration.TransportMode.Header -> {
                    headerParameters.add(name)
                }
                HttpTransportConfiguration.TransportMode.Cookie -> {
                    cookieParameters.add(name)
                }
                HttpTransportConfiguration.TransportMode.Body -> {
                    bodyParameters.add(name)
                }
                HttpTransportConfiguration.TransportMode.Path -> {
                    pathParameters.add(name)
                }
                HttpTransportConfiguration.TransportMode.Query -> {
                    queryParameters.add(name)
                }
            }
        }

        // build request path
        // need to split transport path into path segments and query parameter slots
        val pathSegments = mutableListOf<String>()
        val queryParameterSlots = mutableListOf<String>()
        val transportPath = transport.getPath(info.service.name, operation.name)
        val pathParts = transportPath.split("/")

        // build request headers and body

        // oneway vs request-response
    }

    private fun StringBuilder.appendConsumerResponseParsing(operation: ServiceOperation, transport: HttpTransportConfiguration) {
        /*
            val bodyAsText = response.bodyAsText()
            val body = Json.parseToJsonElement(bodyAsText)

            val respTitle = body.jsonObject["title"]!!.jsonPrimitive.content
            val respDescription = response.headers["description"]!!
            check(respTitle.length in 1..100)

            Todo(
                title = respTitle,
                description = respDescription,
            )
        */


    }
}
