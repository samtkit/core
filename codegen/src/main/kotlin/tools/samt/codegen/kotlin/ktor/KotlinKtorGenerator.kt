package tools.samt.codegen.kotlin.ktor

import tools.samt.codegen.*
import tools.samt.codegen.http.HttpTransportConfiguration
import tools.samt.codegen.kotlin.GeneratedFilePreamble
import tools.samt.codegen.kotlin.KotlinTypesGenerator
import tools.samt.codegen.kotlin.getQualifiedName

object KotlinKtorGenerator : Generator {
    override val name: String = "kotlin-ktor"

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
        if (pack.hasDataTypes()) {
            val packageSource = buildString {
                appendLine(GeneratedFilePreamble)
                appendLine()
                appendLine("package ${pack.getQualifiedName(options)}")
                appendLine()
                appendLine("import io.ktor.util.*")
                appendLine("import kotlinx.serialization.json.*")
                appendLine()

                pack.records.forEach { record ->
                    appendLine("/** Parse and validate record ${record.qualifiedName} */")
                    appendLine("fun `parse ${record.name}`(json: JsonElement): ${record.getQualifiedName(options)} {")
                    for (field in record.fields) {
                        appendLine("    // Parse field ${field.name}")
                        appendLine("    val `field ${field.name}` = run {")
                        if (field.type.isOptional) {
                            appendLine("        val jsonElement = json.jsonObject[\"${field.name}\"] ?: return@run null")
                        } else {
                            appendLine("        val jsonElement = json.jsonObject[\"${field.name}\"]!!")
                        }
                        appendLine("        ${deserializeJsonElement(field.type, options)}")
                        appendLine("    }")
                    }
                    appendLine("    return ${record.getQualifiedName(options)}(")
                    for (field in record.fields) {
                        appendLine("        ${field.name} = `field ${field.name}`,")
                    }
                    appendLine("    )")
                    appendLine("}")
                    appendLine()
                }

                pack.enums.forEach { enum ->
                    val enumName = enum.getQualifiedName(options)
                    appendLine("/** Parse enum ${enum.qualifiedName} */")
                    appendLine("fun `parse ${enum.name}`(json: JsonElement) = when(json.jsonPrimitive.content) {")
                    enum.values.forEach { value ->
                        appendLine("    \"${value}\" -> ${enumName}.${value}")
                    }
                    appendLine("    // Value not found in enum ${enum.qualifiedName}, returning UNKNOWN")
                    appendLine("    else -> ${enumName}.UNKNOWN")
                    appendLine("}")
                }
            }

            val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/KtorMappings.kt"
            val file = CodegenFile(filePath, packageSource)
            emittedFiles.add(file)
        }
    }

    private fun generatePackage(pack: SamtPackage, options: Map<String, String>) {
        if (pack.hasProviderTypes()) {

            // generate general ktor files
            generateKtorServer(pack, options)

            // generate ktor providers
            pack.providers.forEach { provider ->
                val transportConfiguration = provider.transport
                if (transportConfiguration !is HttpTransportConfiguration) {
                    // Skip providers that are not HTTP
                    return@forEach
                }

                val packageSource = buildString {
                    appendLine(GeneratedFilePreamble)
                    appendLine()
                    appendLine("package ${pack.getQualifiedName(options)}")
                    appendLine()

                    appendProvider(provider, transportConfiguration, options)
                }

                val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/${provider.name}.kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }

            // generate ktor consumers
            pack.consumers.forEach { consumer ->
                val provider = consumer.provider.type as ProviderType
                val transportConfiguration = provider.transport
                if (transportConfiguration !is HttpTransportConfiguration) {
                    // Skip consumers that are not HTTP
                    return@forEach
                }

                val packageSource = buildString {
                    appendLine("package ${pack.qualifiedName}")
                    appendLine()

                    appendConsumer(consumer, transportConfiguration, options)
                }

                val filePath = pack.qualifiedName.replace('.', '/') + "Consumer.kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }
        }
    }

    private fun generateKtorServer(pack: SamtPackage, options: Map<String, String>) {
        val packageSource = buildString {
            appendLine(GeneratedFilePreamble)
            appendLine()
            appendLine("package ${pack.getQualifiedName(options)}")
            appendLine()

            appendLine("import io.ktor.http.*")
            appendLine("import io.ktor.serialization.kotlinx.json.*")
            appendLine("import io.ktor.server.plugins.contentnegotiation.*")
            appendLine("import io.ktor.server.response.*")
            appendLine("import io.ktor.server.application.*")
            appendLine("import io.ktor.server.request.*")
            appendLine("import io.ktor.server.routing.*")
            appendLine("import kotlinx.serialization.json.*")
            appendLine()

            appendLine("fun Application.configureSerialization() {")
            appendLine("    install(ContentNegotiation) {")
            appendLine("        json()")
            appendLine("    }")
            appendLine("    routing {")

            for (provider in pack.providers) {
                val implementedServices = provider.implements.map { ProviderInfo(it) }
                appendLine("        route${provider.name}(")
                for (info in implementedServices) {
                    provider.implements.joinToString(" */, /* ") { it.service.getQualifiedName(options) }
                    appendLine(
                        "            ${info.serviceArgumentName} = TODO(\"Implement ${
                            info.service.getQualifiedName(
                                options
                            )
                        }\"),"
                    )
                }
                appendLine("        )")
            }

            appendLine("    }")
            appendLine("}")
        }

        val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/KtorServer.kt"
        val file = CodegenFile(filePath, packageSource)
        emittedFiles.add(file)
    }

    data class ProviderInfo(val implements: ProviderImplements) {
        val reference = implements.service
        val service = reference.type as ServiceType
        val serviceArgumentName = service.name.replaceFirstChar { it.lowercase() }
    }

    private fun StringBuilder.appendProvider(
        provider: ProviderType,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        appendLine("import io.ktor.http.*")
        appendLine("import io.ktor.serialization.kotlinx.json.*")
        appendLine("import io.ktor.server.plugins.contentnegotiation.*")
        appendLine("import io.ktor.server.response.*")
        appendLine("import io.ktor.server.application.*")
        appendLine("import io.ktor.server.request.*")
        appendLine("import io.ktor.server.routing.*")
        appendLine("import kotlinx.serialization.json.*")
        appendLine()

        val implementedServices = provider.implements.map { ProviderInfo(it) }
        appendLine("// ${transportConfiguration.exceptionMap}")
        appendLine("/** Connector for SAMT provider ${provider.name} */")
        appendLine("fun Routing.route${provider.name}(")
        for (info in implementedServices) {
            appendLine("    ${info.serviceArgumentName}: ${info.reference.getQualifiedName(options)},")
        }
        appendLine(") {")
        appendUtilities()
        implementedServices.forEach { info ->
            appendProviderOperations(info, transportConfiguration, options)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendUtilities() {
        appendLine("    /** Utility used to convert string to JSON element */")
        appendLine("    fun String.toJson() = Json.parseToJsonElement(this)")
        appendLine("    /** Utility used to convert string to JSON element or null */")
        appendLine("    fun String.toJsonOrNull() = Json.parseToJsonElement(this).takeUnless { it is JsonNull }")
        appendLine()
    }

    private fun StringBuilder.appendProviderOperations(
        info: ProviderInfo,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        val service = info.service
        info.implements.operations.forEach { operation ->
            appendLine("    // Handler for SAMT Service ${info.service.name}")
            appendLine("    route(\"${transportConfiguration.getPath(service.name)}\") {")
            appendProviderOperation(operation, info, service, transportConfiguration, options)
            appendLine("    }")
            appendLine()
        }
    }

    private fun StringBuilder.appendProviderOperation(
        operation: ServiceOperation,
        info: ProviderInfo,
        service: ServiceType,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        when (operation) {
            is RequestResponseOperation -> {
                appendLine("        // Handler for SAMT operation ${operation.name}")
                appendLine("        ${getKtorRoute(service, operation, transportConfiguration)} {")

                appendParsingPreamble()

                operation.parameters.forEach { parameter ->
                    appendParameterParsing(service, operation, parameter, transportConfiguration, options)
                }

                appendLine("            // Call user provided implementation")
                appendLine("            val response = ${getServiceCall(info, operation)}")
                appendLine()

                appendLine("            call.respond(response)")
                appendLine("        }")
                appendLine()
            }
            is OnewayOperation -> {
                appendLine("        // Handler for SAMT oneway operation ${operation.name}")
                appendLine("        ${getKtorRoute(service, operation, transportConfiguration)} {")

                appendParsingPreamble()

                operation.parameters.forEach { parameter ->
                    appendParameterParsing(service, operation, parameter, transportConfiguration, options)
                }

                appendLine("            // Use launch to handle the request asynchronously, not waiting for the response")
                appendLine("            launch {")
                appendLine("                // Call user provided implementation")
                appendLine("                ${getServiceCall(info, operation)}")
                appendLine("            }")
                appendLine()

                appendLine("            // Oneway operation always returns 204 No Content")
                appendLine("            call.respond(HttpStatusCode.NoContent)")
                appendLine("        }")
            }
        }
    }

    private fun StringBuilder.appendParsingPreamble() {
        appendLine("            // Parse body lazily in case no parameter is transported in the body")
        appendLine("            val bodyAsText = call.receiveText()")
        appendLine("            val body by lazy { bodyAsText.toJson() }")
        appendLine()
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
        appendLine("import kotlinx.coroutines.runBlocking")
        appendLine("import kotlinx.serialization.json.*")

        val implementedServices = consumer.uses.map { ConsumerInfo(it) }
        val serviceArguments = implementedServices.joinToString { info ->
            "${info.serviceArgumentName}: ${info.reference.getQualifiedName(options)}"
        }
        appendLine("// ${transportConfiguration.exceptionMap}")
        appendLine("class ${consumer.name}() {")
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

    private fun getKtorRoute(
        service: ServiceType,
        operation: ServiceOperation,
        transportConfiguration: HttpTransportConfiguration,
    ): String {
        val method = when (transportConfiguration.getMethod(service.name, operation.name)) {
            HttpTransportConfiguration.HttpMethod.Get -> "get"
            HttpTransportConfiguration.HttpMethod.Post -> "post"
            HttpTransportConfiguration.HttpMethod.Put -> "put"
            HttpTransportConfiguration.HttpMethod.Delete -> "delete"
            HttpTransportConfiguration.HttpMethod.Patch -> "patch"
        }
        val path = transportConfiguration.getPath(service.name, operation.name)
        return "${method}(\"${path}\")"
    }

    private fun getServiceCall(info: ProviderInfo, operation: ServiceOperation): String {
        return "${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { "`parameter ${it.name}`" }})"
    }

    private fun StringBuilder.appendParameterParsing(
        service: ServiceType,
        operation: ServiceOperation,
        parameter: ServiceOperationParameter,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        appendLine("            // Parse parameter ${parameter.name}")
        appendLine("            val `parameter ${parameter.name}` = run {")
        val transportMode = transportConfiguration.getTransportMode(service.name, operation.name, parameter.name)
        appendParameterDeserialization(parameter, transportMode, options)
        appendLine("            }")
        appendLine()
    }

    private fun StringBuilder.appendParameterDeserialization(
        parameter: ServiceOperationParameter,
        transportMode: HttpTransportConfiguration.TransportMode,
        options: Map<String, String>,
    ) {
        appendReadJsonElement(parameter, transportMode)
        appendLine("                ${deserializeJsonElement(parameter.type, options)}")
    }

    private fun StringBuilder.appendReadJsonElement(
        parameter: ServiceOperationParameter,
        transportMode: HttpTransportConfiguration.TransportMode,
    ) {
        appendLine("                // Read from ${transportMode.name.lowercase()}")
        append("                val jsonElement = ")
        if (parameter.type.isOptional) {
            when (transportMode) {
                HttpTransportConfiguration.TransportMode.Body -> append("body.jsonObject[\"${parameter.name}\"]?.takeUnless { it is JsonNull }")
                HttpTransportConfiguration.TransportMode.Query -> append("call.request.queryParameters[\"${parameter.name}\"]?.toJsonOrNull()")
                HttpTransportConfiguration.TransportMode.Path -> append("call.parameters[\"${parameter.name}\"]?.toJsonOrNull()")
                HttpTransportConfiguration.TransportMode.Header -> append("call.request.headers[\"${parameter.name}\"]?.toJsonOrNull()")
                HttpTransportConfiguration.TransportMode.Cookie -> append("call.request.cookies[\"${parameter.name}\"]?.toJsonOrNull()")
            }
            append(" ?: return@run null")
        } else {
            when (transportMode) {
                HttpTransportConfiguration.TransportMode.Body -> append("body.jsonObject[\"${parameter.name}\"]!!")
                HttpTransportConfiguration.TransportMode.Query -> append("call.request.queryParameters[\"${parameter.name}\"]!!.toJson()")
                HttpTransportConfiguration.TransportMode.Path -> append("call.parameters[\"${parameter.name}\"]!!.toJson()")
                HttpTransportConfiguration.TransportMode.Header -> append("call.request.headers[\"${parameter.name}\"]!!.toJson()")
                HttpTransportConfiguration.TransportMode.Cookie -> append("call.request.cookies[\"${parameter.name}\"]!!.toJson()")
            }
        }
        appendLine()
    }

    private fun deserializeJsonElement(typeReference: TypeReference, options: Map<String, String>): String {
        return when (val type = typeReference.type) {
            is LiteralType -> when (type) {
                is StringType -> "jsonElement.jsonPrimitive.content"
                is BytesType -> "jsonElement.jsonPrimitive.content.decodeBase64Bytes()"
                is IntType -> "jsonElement.jsonPrimitive.int"
                is LongType -> "jsonElement.jsonPrimitive.long"
                is FloatType -> "jsonElement.jsonPrimitive.float"
                is DoubleType -> "jsonElement.jsonPrimitive.double"
                is DecimalType -> "jsonElement.jsonPrimitive.content.let { java.math.BigDecimal(it) }"
                is BooleanType -> "jsonElement.jsonPrimitive.boolean"
                is DateType -> "jsonElement.jsonPrimitive.content?.let { java.time.LocalDate.parse(it) }"
                is DateTimeType -> "jsonElement.jsonPrimitive.content?.let { java.time.LocalDateTime.parse(it) }"
                is DurationType -> "jsonElement.jsonPrimitive.content?.let { java.time.Duration.parse(it) }"
                else -> error("Unsupported literal type: ${this.javaClass.simpleName}")
            } + literalConstraintSuffix(typeReference)

            is ListType -> "jsonElement.jsonArray.map { ${deserializeJsonElement(type.elementType, options)} }"
            is MapType -> "jsonElement.jsonObject.mapValues { ${deserializeJsonElement(type.valueType, options)} }"

            is UserType -> "`parse ${type.name}`(jsonElement)"

            else -> error("Unsupported type: ${javaClass.simpleName}")
        }
    }

    private fun literalConstraintSuffix(typeReference: TypeReference): String {
        val conditions = buildList {
            typeReference.rangeConstraint?.let { constraint ->
                constraint.lowerBound?.let {
                    add("it >= ${constraint.lowerBound}")
                }
                constraint.upperBound?.let {
                    add("it <= ${constraint.upperBound}")
                }
            }
            typeReference.sizeConstraint?.let { constraint ->
                val property = if (typeReference.type is StringType) "length" else "size"
                constraint.lowerBound?.let {
                    add("it.${property} >= ${constraint.lowerBound}")
                }
                constraint.upperBound?.let {
                    add("it.${property} <= ${constraint.upperBound}")
                }
            }
            typeReference.patternConstraint?.let { constraint ->
                add("it.matches(\"${constraint.pattern}\")")
            }
            typeReference.valueConstraint?.let { constraint ->
                add("it == ${constraint.value})")
            }
        }

        if (conditions.isEmpty()) {
            return ""
        }

        return ".also { require(${conditions.joinToString(" && ")}) }"
    }

    private fun SamtPackage.hasDataTypes(): Boolean {
        return records.isNotEmpty() || enums.isNotEmpty()
    }

    private fun SamtPackage.hasProviderTypes(): Boolean {
        return providers.isNotEmpty()
    }
}
