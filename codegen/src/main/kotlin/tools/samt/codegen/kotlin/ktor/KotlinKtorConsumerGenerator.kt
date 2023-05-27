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
        val service = uses.service
        val implementedOperations = uses.operations
        val notImplementedOperations = service.operations.filter { serviceOp -> implementedOperations.none { it.name == serviceOp.name } }
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
        appendLine("import kotlinx.coroutines.*")

        val implementedServices = consumer.uses.map { ConsumerInfo(it) }
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
        appendLine("    /** Used to launch oneway operations asynchronously */")
        appendLine("    private val onewayScope = CoroutineScope(Dispatchers.IO)")
        appendLine()

        info.implementedOperations.forEach { operation ->
            val operationParameters = operation.parameters.joinToString { "${it.name}: ${it.type.getQualifiedName(options)}" }

            when (operation) {
                is RequestResponseOperation -> {
                    if (operation.isAsync) {
                        appendLine("    override suspend fun ${operation.name}($operationParameters): ${operation.returnType?.getQualifiedName(options) ?: "Unit"} {")
                    } else {
                        appendLine("    override fun ${operation.name}($operationParameters): ${operation.returnType?.getQualifiedName(options) ?: "Unit"} = runBlocking {")
                    }

                    appendConsumerServiceCall(info, operation, transportConfiguration, options)
                    appendCheckResponseStatus(operation)
                    appendConsumerResponseParsing(operation, transportConfiguration, options)

                    appendLine("    }")
                }

                is OnewayOperation -> {
                    appendLine("    override fun ${operation.name}($operationParameters): Unit {")
                    appendLine("        onewayScope.launch {")

                    appendConsumerServiceCall(info, operation, transportConfiguration, options)
                    appendCheckResponseStatus(operation)

                    appendLine("        }")
                    appendLine("    }")
                }
            }
        }

        info.notImplementedOperations.forEach { operation ->
            val operationParameters = operation.parameters.joinToString { "${it.name}: ${it.type.getQualifiedName(options)}" }

            when (operation) {
                is RequestResponseOperation -> {
                    if (operation.isAsync) {
                        appendLine("    override suspend fun ${operation.name}($operationParameters): ${operation.returnType?.getQualifiedName(options) ?: "Unit"} {")
                    } else {
                        appendLine("    override fun ${operation.name}($operationParameters): ${operation.returnType?.getQualifiedName(options) ?: "Unit"}")
                    }
                }

                is OnewayOperation -> {
                    appendLine("    override fun ${operation.name}($operationParameters): Unit")
                }
            }
            appendLine("        = error(\"Not used in model and therefore not generated\")")
        }
    }

    private fun StringBuilder.appendConsumerServiceCall(info: ConsumerInfo, operation: ServiceOperation, transport: HttpTransportConfiguration, options: Map<String, String>) {
        // collect parameters for each transport type
        val headerParameters = mutableMapOf<String, ServiceOperationParameter>()
        val cookieParameters = mutableMapOf<String, ServiceOperationParameter>()
        val bodyParameters = mutableMapOf<String, ServiceOperationParameter>()
        val pathParameters = mutableMapOf<String, ServiceOperationParameter>()
        val queryParameters = mutableMapOf<String, ServiceOperationParameter>()
        operation.parameters.forEach {
            val name = it.name
            when (transport.getTransportMode(info.service.name, operation.name, name)) {
                HttpTransportConfiguration.TransportMode.Header -> {
                    headerParameters[name] = it
                }
                HttpTransportConfiguration.TransportMode.Cookie -> {
                    cookieParameters[name] = it
                }
                HttpTransportConfiguration.TransportMode.Body -> {
                    bodyParameters[name] = it
                }
                HttpTransportConfiguration.TransportMode.Path -> {
                    pathParameters[name] = it
                }
                HttpTransportConfiguration.TransportMode.Query -> {
                    queryParameters[name] = it
                }
            }
        }

        // build request headers and body
        appendLine("        val response = client.request(`consumer baseUrl`) {")

        // build request path
        // need to split transport path into path segments and query parameter slots
        // remove first empty component (paths start with a / so the first component is always empty)
        val transportPath = transport.getPath(info.service.name, operation.name)
        val transportPathComponents = transportPath.split("/")
        appendLine("                url(`consumer baseUrl`) {")
        transportPathComponents.drop(1).map {
            if (it.startsWith("{") && it.endsWith("}")) {
                val parameterName = it.substring(1, it.length - 1)
                require(pathParameters.contains(parameterName)) { "${operation.name}: path parameter $parameterName is not a known path parameter" }
                appendLine("                    appendPathSegments($parameterName)")
            } else {
                appendLine("                    appendPathSegments(\"$it\")")
            }
        }
        appendLine("                }")

        // serialization mode
        when (transport.serializationMode) {
            HttpTransportConfiguration.SerializationMode.Json -> appendLine("                contentType(ContentType.Application.Json)")
        }

        // transport method
        val transportMethod = transport.getMethod(info.service.name, operation.name)
        appendLine("                method = HttpMethod.$transportMethod")

        // header parameters
        headerParameters.forEach {
            appendLine("                headers[\"${it.key}\"] = ${encodeJsonElement(it.value.type, options)}")
        }

        // cookie parameters
        cookieParameters.forEach {
            appendLine("                cookie(\"${it.key}\", ${encodeJsonElement(it.value.type, options)})")
        }

        // body parameters
        appendLine("                setBody(")
        appendLine("                    buildJsonObject {")
        bodyParameters.forEach { (name, parameter) ->
            appendLine("                        put(\"$name\", ${encodeJsonElement(parameter.type, options)})")
        }
        appendLine("                    }")
        appendLine("                )")

        appendLine("            }")
    }

    private fun StringBuilder.appendCheckResponseStatus(operation: ServiceOperation) {
        appendLine("        check(!response.status.isSuccess()) { \"${operation.name} failed with status \${response.status}\" }")
    }

    private fun StringBuilder.appendConsumerResponseParsing(operation: RequestResponseOperation, transport: HttpTransportConfiguration, options: Map<String, String>) {
        operation.returnType?.let { returnType ->
            appendLine("        val bodyAsText = response.bodyAsText()")
            appendLine("        val jsonElement = Json.parseToJsonElement(bodyAsText)")
            appendLine("        return ${decodeJsonElement(returnType, options)}")
        }
    }
}
