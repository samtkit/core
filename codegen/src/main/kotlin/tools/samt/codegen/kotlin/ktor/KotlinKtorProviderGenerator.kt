package tools.samt.codegen.kotlin.ktor

import tools.samt.api.plugin.CodegenFile
import tools.samt.api.plugin.Generator
import tools.samt.api.plugin.GeneratorParams
import tools.samt.api.transports.http.HttpMethod
import tools.samt.api.transports.http.TransportMode
import tools.samt.api.types.*
import tools.samt.codegen.http.HttpTransportConfiguration
import tools.samt.codegen.kotlin.GeneratedFilePreamble
import tools.samt.codegen.kotlin.KotlinTypesGenerator
import tools.samt.codegen.kotlin.getQualifiedName

object KotlinKtorProviderGenerator : Generator {
    override val name: String = "kotlin-ktor-provider"
    private const val skipKtorServer = "skipKtorServer"

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
        val relevantProviders = pack.providers.filter { it.transport is HttpTransportConfiguration }
        if (relevantProviders.isNotEmpty()) {
            if (options[skipKtorServer] != "true") {
                // generate general ktor files
                generateKtorServer(pack, options)
            }

            // generate ktor providers
            relevantProviders.forEach { provider ->
                val transportConfiguration = provider.transport
                check(transportConfiguration is HttpTransportConfiguration)

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

    data class ProviderInfo(val implements: ProvidedService) {
        val service = implements.service
        val serviceArgumentName = implements.service.name.replaceFirstChar { it.lowercase() }
    }

    private fun StringBuilder.appendProvider(
        provider: ProviderType,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        appendLine("import io.ktor.http.*")
        appendLine("import io.ktor.serialization.kotlinx.json.*")
        appendLine("import io.ktor.server.application.*")
        appendLine("import io.ktor.server.plugins.contentnegotiation.*")
        appendLine("import io.ktor.server.request.*")
        appendLine("import io.ktor.server.response.*")
        appendLine("import io.ktor.server.routing.*")
        appendLine("import io.ktor.util.*")
        appendLine("import kotlinx.serialization.json.*")
        appendLine()

        val implementedServices = provider.implements.map { ProviderInfo(it) }
        appendLine("/** Connector for SAMT provider ${provider.name} */")
        appendLine("fun Routing.route${provider.name}(")
        for (info in implementedServices) {
            appendLine("    ${info.serviceArgumentName}: ${info.service.getQualifiedName(options)},")
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
        appendLine("    // Handler for SAMT Service ${info.service.name}")
        appendLine("    route(\"${transportConfiguration.getServicePath(service.name)}\") {")
        info.implements.implementedOperations.forEach { operation ->
            appendProviderOperation(operation, info, service, transportConfiguration, options)
        }
        appendLine("    }")
        appendLine()
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
                    appendParameterDecoding(service, operation, parameter, transportConfiguration, options)
                }

                appendLine("            // Call user provided implementation")
                val returnType = operation.returnType
                if (returnType != null) {
                    appendLine("            val value = ${getServiceCall(info, operation)}")
                    appendLine()
                    appendLine("            // Encode response")
                    appendLine("            val response = ${encodeJsonElement(returnType, options)}")
                    appendLine()
                    appendLine("            // Return response with 200 OK")
                    appendLine("            call.respond(HttpStatusCode.OK, response)")
                } else {
                    appendLine("            ${getServiceCall(info, operation)}")
                    appendLine()
                    appendLine("            // Return 204 No Content")
                    appendLine("            call.respond(HttpStatusCode.NoContent)")
                }

                appendLine("        }")
                appendLine()
            }
            is OnewayOperation -> {
                appendLine("        // Handler for SAMT oneway operation ${operation.name}")
                appendLine("        ${getKtorRoute(service, operation, transportConfiguration)} {")

                appendParsingPreamble()

                operation.parameters.forEach { parameter ->
                    appendParameterDecoding(service, operation, parameter, transportConfiguration, options)
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

    private fun getKtorRoute(
        service: ServiceType,
        operation: ServiceOperation,
        transportConfiguration: HttpTransportConfiguration,
    ): String {
        val method = when (transportConfiguration.getMethod(service.name, operation.name)) {
            HttpMethod.Get -> "get"
            HttpMethod.Post -> "post"
            HttpMethod.Put -> "put"
            HttpMethod.Delete -> "delete"
            HttpMethod.Patch -> "patch"
        }
        val path = transportConfiguration.getPath(service.name, operation.name)
        return "${method}(\"${path}\")"
    }

    private fun getServiceCall(info: ProviderInfo, operation: ServiceOperation): String {
        return "${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { "`parameter ${it.name}`" }})"
    }

    private fun StringBuilder.appendParameterDecoding(
        service: ServiceType,
        operation: ServiceOperation,
        parameter: ServiceOperationParameter,
        transportConfiguration: HttpTransportConfiguration,
        options: Map<String, String>,
    ) {
        appendLine("            // Decode parameter ${parameter.name}")
        appendLine("            val `parameter ${parameter.name}` = run {")
        val transportMode = transportConfiguration.getTransportMode(service.name, operation.name, parameter.name)
        appendParameterDeserialization(parameter, transportMode, options)
        appendLine("            }")
        appendLine()
    }

    private fun StringBuilder.appendParameterDeserialization(
        parameter: ServiceOperationParameter,
        transportMode: TransportMode,
        options: Map<String, String>,
    ) {
        appendReadParameterJsonElement(parameter, transportMode)
        appendLine("                ${decodeJsonElement(parameter.type, options)}")
    }

    private fun StringBuilder.appendReadParameterJsonElement(
        parameter: ServiceOperationParameter,
        transportMode: TransportMode,
    ) {
        appendLine("                // Read from ${transportMode.name.replaceFirstChar { it.lowercase() }}")
        append("                val jsonElement = ")
        if (parameter.type.isRuntimeOptional) {
            when (transportMode) {
                TransportMode.Body -> append("body.jsonObject[\"${parameter.name}\"]?.takeUnless { it is JsonNull }")
                TransportMode.QueryParameter -> append("call.request.queryParameters[\"${parameter.name}\"]?.toJsonOrNull()")
                TransportMode.Path -> append("call.parameters[\"${parameter.name}\"]?.toJsonOrNull()")
                TransportMode.Header -> append("call.request.headers[\"${parameter.name}\"]?.toJsonOrNull()")
                TransportMode.Cookie -> append("call.request.cookies[\"${parameter.name}\"]?.toJsonOrNull()")
            }
            append(" ?: return@run null")
        } else {
            when (transportMode) {
                TransportMode.Body -> append("body.jsonObject[\"${parameter.name}\"]!!")
                TransportMode.QueryParameter -> append("call.request.queryParameters[\"${parameter.name}\"]!!.toJson()")
                TransportMode.Path -> append("call.parameters[\"${parameter.name}\"]!!.toJson()")
                TransportMode.Header -> append("call.request.headers[\"${parameter.name}\"]!!.toJson()")
                TransportMode.Cookie -> append("call.request.cookies[\"${parameter.name}\"]!!.toJson()")
            }
        }
        appendLine()
    }
}
