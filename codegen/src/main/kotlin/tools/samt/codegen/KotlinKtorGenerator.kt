package tools.samt.codegen

class KotlinKtorGenerator : Generator {
    override val identifier: String = "kotlin-ktor"

    override fun generate(generatorParams: GeneratorParams): List<CodegenFile> {
        generatorParams.packages.forEach {
            generatePackage(it)
        }
        return KotlinTypesGenerator().generate(generatorParams) + emittedFiles
    }

    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generatePackage(pack: SamtPackage) {
        if (pack.hasProviderTypes()) {

            // generate general ktor files
            generateKtorServer(pack)

            // generate ktor providers
            pack.providers.forEach { provider ->
                val transportConfiguration = provider.transport
                if (transportConfiguration !is HttpTransportConfiguration) {
                    // Skip providers that are not HTTP
                    return@forEach
                }

                val packageSource = buildString {
                    appendLine("package ${pack.qualifiedName}")
                    appendLine()

                    appendProvider(provider, transportConfiguration)
                }

                val filePath = pack.qualifiedName.replace('.', '/') + "Provider.kt"
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

                    appendConsumer(consumer, transportConfiguration)
                }

                val filePath = pack.qualifiedName.replace('.', '/') + "Consumer.kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }
        }
    }

    private fun generateKtorServer(pack: SamtPackage) {
        val packageSource = buildString {
            appendLine("package ${pack.qualifiedName}")
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
                append("        route${provider.name}(")
                append("/* ")
                append(provider.implements.joinToString(" */, /* ") { it.service.qualifiedName })
                appendLine("*/)")
            }

            appendLine("    }")
            appendLine("}")
        }

        val filePath = pack.qualifiedName.replace('.', '/') + "Server.kt"
        val file = CodegenFile(filePath, packageSource)
        emittedFiles.add(file)
    }

    data class ProviderInfo(val implements: ProviderImplements) {
        val reference = implements.service
        val service = reference.type as ServiceType
        val serviceArgumentName = service.name.replaceFirstChar { it.lowercase() }
    }

    private fun StringBuilder.appendProvider(provider: ProviderType, transportConfiguration: HttpTransportConfiguration) {
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
        val serviceArguments = implementedServices.joinToString { info ->
            "${info.serviceArgumentName}: ${info.reference.qualifiedName}"
        }
        appendLine("// ${transportConfiguration.exceptionMap}")
        appendLine("fun Routing.route${provider.name}($serviceArguments) {")
        implementedServices.forEach { info ->
            appendProviderOperations(info, transportConfiguration)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendProviderOperations(info: ProviderInfo, transportConfiguration: HttpTransportConfiguration) {
        val service = info.service
        info.implements.operations.forEach { operation ->
            when (operation) {
                is RequestResponseOperation -> {
                    appendLine("    ${getKtorRoute(service, operation, transportConfiguration)} {")

                    appendParsingPreamble()

                    operation.parameters.forEach { parameter ->
                        // TODO complexer data types than string
                        appendParameterParsing(service, operation, parameter, transportConfiguration)
                    }
                    appendLine()

                    // TODO Config: HTTP status code
                    // TODO serialize response correctly
                    // TODO validate response
                    if (operation.returnType != null) {
                        appendLine("        val response = ${getServiceCall(info, operation)}")
                        appendLine("        call.respond(response)")
                        appendLine("    }")
                    } else {
                        appendLine("        ${getServiceCall(info, operation)}")
                        appendLine("        call.respond(HttpStatusCode.NoContent)")
                        appendLine("    }")
                    }
                }

                is OnewayOperation -> {
                    // TODO Config: HTTP method?
                    // TODO Config: URL?
                    appendLine("    ${getKtorRoute(service, operation, transportConfiguration)} {")
                    appendParsingPreamble()

                    operation.parameters.forEach { parameter ->
                        appendParameterParsing(service, operation, parameter, transportConfiguration)
                    }

                    appendLine("        launch {")
                    appendLine("            ${getServiceCall(info, operation)}")
                    appendLine("        }")

                    appendLine("        call.respond(HttpStatusCode.NoContent)")
                    appendLine("    }")
                }
            }
        }
    }

    private fun StringBuilder.appendParsingPreamble() {
        appendLine("        val bodyAsText = call.receiveText()")
        appendLine("        val body = Json.parseToJsonElement(bodyAsText)")
        appendLine()
    }

    data class ConsumerInfo(val uses: ConsumerUses) {
        val reference = uses.service
        val service = reference.type as ServiceType
        val serviceArgumentName = service.name.replaceFirstChar { it.lowercase() }
    }

    private fun StringBuilder.appendConsumer(consumer: ConsumerType, transportConfiguration: HttpTransportConfiguration) {
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
            "${info.serviceArgumentName}: ${info.reference.qualifiedName}"
        }
        appendLine("// ${transportConfiguration.exceptionMap}")
        appendLine("class ${consumer.name}() {")
        implementedServices.forEach { info ->
            appendConsumerOperations(info, transportConfiguration)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendConsumerOperations(info: ConsumerInfo, transportConfiguration: HttpTransportConfiguration) {
        appendLine("    private val client = HttpClient(CIO) {")
        appendLine("        install(ContentNegotiation) {")
        appendLine("            json()")
        appendLine("        }")
        appendLine("    }")
        appendLine()

        val service = info.service
        info.uses.operations.forEach { operation ->
            val operationParameters = operation.parameters.joinToString { "${it.name}: ${it.type.qualifiedName}" }

            when (operation) {
                is RequestResponseOperation -> {
                    if (operation.returnType != null) {
                        appendLine("    override fun ${operation.name}($operationParameters): ${operation.returnType!!.qualifiedName} {")
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

    private fun getKtorRoute(service: ServiceType, operation: ServiceOperation, transportConfiguration: HttpTransportConfiguration): String {
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
        return "${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { it.name }})"
    }

    private fun StringBuilder.appendParameterParsing(service: ServiceType, operation: ServiceOperation, parameter: ServiceOperationParameter, transportConfiguration: HttpTransportConfiguration) {
        appendParameterDeserialization(service, operation, parameter, transportConfiguration)
        appendParameterConstraints(parameter)
        appendLine()
    }

    private fun StringBuilder.appendParameterDeserialization(service: ServiceType, operation: ServiceOperation, parameter: ServiceOperationParameter, transportConfiguration: HttpTransportConfiguration) {
        // TODO Config: From Body / from Query / from Path etc.
        // TODO error and null handling
        // TODO complexer data types than string

        when(transportConfiguration.getTransportMode(service.name, operation.name, parameter.name)) {
            HttpTransportConfiguration.TransportMode.Body -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = body.jsonObject[\"${parameter.name}\"]?.jsonPrimitive?.contentOrNull")
                } else {
                    appendLine("        val ${parameter.name} = body.jsonObject.getValue(\"${parameter.name}\").jsonPrimitive.content")
                }
            }
            HttpTransportConfiguration.TransportMode.Query -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = call.request.queryParameters[\"${parameter.name}\"]")
                } else {
                    appendLine("        val ${parameter.name} = call.request.queryParameters.getValue(\"${parameter.name}\")")
                }
            }
            HttpTransportConfiguration.TransportMode.Path -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = call.parameters[\"${parameter.name}\"]")
                } else {
                    appendLine("        val ${parameter.name} = call.parameters.getValue(\"${parameter.name}\")")
                }
            }
            HttpTransportConfiguration.TransportMode.Header -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = call.request.headers.get(\"${parameter.name}\")")
                } else {
                    appendLine("        val ${parameter.name} = call.request.headers.get(\"${parameter.name}\")!!")
                }
            }
            HttpTransportConfiguration.TransportMode.Cookie -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = call.request.cookies.get(\"${parameter.name}\")")
                } else {
                    appendLine("        val ${parameter.name} = call.request.cookies.get(\"${parameter.name}\")!!")
                }
            }
        }
    }

    private fun StringBuilder.appendParameterConstraints(parameter: ServiceOperationParameter) {
        // TODO constraints within map or list or record field
        parameter.type.rangeConstraint?.let {
            appendLine("        require(${parameter.name}.length in ${it.lowerBound}..${it.upperBound}) { \"${parameter.name} must be between ${it.lowerBound} and ${it.upperBound} characters long\" }")
        }
        parameter.type.sizeConstraint?.let {
            appendLine("        require(${parameter.name}.size in ${it.lowerBound}..${it.upperBound}) { \"${parameter.name} must be between ${it.lowerBound} and ${it.upperBound} elements long\" }")
        }
        parameter.type.patternConstraint?.let {
            appendLine("        require(${parameter.name}.matches(\"${it.pattern}\") { \"${parameter.name} does not adhere to required pattern '${it.pattern}'\" }")
        }
        parameter.type.valueConstraint?.let {
            appendLine("        require(${parameter.name} == ${it.value}) { \"${parameter.name} does not equal '${it.value}'\" }")
        }
    }

    private fun SamtPackage.hasProviderTypes(): Boolean {
        return providers.isNotEmpty()
    }
}
