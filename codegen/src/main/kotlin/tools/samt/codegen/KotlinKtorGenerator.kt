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

                val filePath = pack.qualifiedName.replace('.', '/') + ".kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }
        }
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
        appendLine("fun Routing.route${provider.name}($serviceArguments) {")
        implementedServices.forEach { info ->
            appendProviderOperations(info, transportConfiguration)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendProviderOperations(info: ProviderInfo, transportConfiguration: HttpTransportConfiguration) {
        info.implements.operations.forEach { operation ->
            when (operation) {
                is RequestResponseOperation -> {
                    appendLine("    ${getKtorRoute(operation, transportConfiguration)} {")

                    appendParsingPreamble()

                    operation.parameters.forEach { parameter ->
                        // TODO complexer data types than string
                        appendParameterParsing(parameter, transportConfiguration)
                    }
                    appendLine()

                    appendLine("        val response = ${getServiceCall(info, operation)}")

                    // TODO Config: HTTP status code

                    // TODO serialize response correctly
                    // TODO validate response
                    appendLine("        call.respond(response)")
                    appendLine("    }")
                }

                is OnewayOperation -> {
                    // TODO Config: HTTP method?
                    // TODO Config: URL?
                    appendLine("    ${getKtorRoute(operation, transportConfiguration)} {")
                    appendParsingPreamble()

                    operation.parameters.forEach { parameter ->
                        appendParameterParsing(parameter, transportConfiguration)
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

    private fun getKtorRoute(operation: ServiceOperation, transportConfiguration: HttpTransportConfiguration): String {
        val method = when (transportConfiguration.getMethod(operation)) {
            HttpTransportConfiguration.HttpMethod.Get -> "get"
            HttpTransportConfiguration.HttpMethod.Post -> "post"
            HttpTransportConfiguration.HttpMethod.Put -> "put"
            HttpTransportConfiguration.HttpMethod.Delete -> "delete"
            HttpTransportConfiguration.HttpMethod.Patch -> "patch"
        }
        val path = transportConfiguration.getPath(operation)
        return "${method}(\"${path}\")"
    }

    private fun getServiceCall(info: ProviderInfo, operation: ServiceOperation): String {
        return "${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { it.name }})"
    }

    private fun StringBuilder.appendParameterParsing(parameter: ServiceOperationParameter, transportConfiguration: HttpTransportConfiguration) {
        appendParameterDeserialization(parameter, transportConfiguration)
        appendParameterConstraints(parameter)
        appendLine()
    }

    private fun StringBuilder.appendParameterDeserialization(parameter: ServiceOperationParameter, transportConfiguration: HttpTransportConfiguration) {
        // TODO Config: From Body / from Query / from Path etc.
        // TODO error and null handling
        // TODO complexer data types than string

        when(transportConfiguration.getTransportMode(parameter)) {
            HttpTransportConfiguration.TransportMode.Body -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = body.jsonObject[\"${parameter.name}\"]?.jsonPrimitive?.contentOrNull")
                } else {
                    appendLine("        val ${parameter.name} = body.jsonObject.getValue(\"${parameter.name}\").jsonPrimitive.content")
                }
            }
            HttpTransportConfiguration.TransportMode.Query -> {
                if (parameter.type.isOptional) {
                    appendLine("        val ${parameter.name} = queryParameters[\"${parameter.name}\"]")
                } else {
                    appendLine("        val ${parameter.name} = queryParameters.getValue(\"${parameter.name}\")")
                }
            }
            HttpTransportConfiguration.TransportMode.Path -> TODO()
            HttpTransportConfiguration.TransportMode.Header -> TODO()
            HttpTransportConfiguration.TransportMode.Cookie -> TODO()
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
