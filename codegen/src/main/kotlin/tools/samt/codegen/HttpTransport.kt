package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.parser.*
import tools.samt.semantic.Package

// TODO: refactor diagnostic controller support

class HttpTransportConfigurationParser: TransportConfigurationParser {
    override val transportName: String
        get() = "http"

    override fun default(): TransportConfiguration {
        require(false) { "Not implemented: Default Configuration" }
        return HttpTransportConfiguration(
            serializationMode = HttpTransportConfiguration.SerializationMode.Json,
            services = emptyList(),
        )
    }

    class Params(
        override val configObjectNode: ObjectNode,
        private val controller: DiagnosticController
    ) : TransportConfigurationParserParams {

        override fun reportError(message: String) {
            controller.reportGlobalError(message)
        }

        override fun reportWarning(message: String) {
            controller.reportGlobalWarning(message)
        }

        override fun reportInfo(message: String) {
            controller.reportGlobalInfo(message)
        }
    }

    override fun parse(params: TransportConfigurationParserParams): HttpTransportConfiguration? {
        require(params is Params) { "Invalid params type" }

        val fields = parseObjectNode(params.configObjectNode)

        val serializationMode = if (fields.containsKey("serialization")) {
            val serializationConfig = fields["serialization"]!!
            if (serializationConfig is StringNode) {
                when (serializationConfig.value) {
                    "json" -> HttpTransportConfiguration.SerializationMode.Json
                    else -> {
                        // unknown serialization mode
                        params.reportError("Unknown serialization mode '${serializationConfig.value}', defaulting to 'json'")
                        HttpTransportConfiguration.SerializationMode.Json
                    }
                }
            } else {
                // invalid serialization mode type, expected string
                params.reportError("Invalid value for 'serialization', defaulting to 'json'")
                HttpTransportConfiguration.SerializationMode.Json
            }
        } else {
            HttpTransportConfiguration.SerializationMode.Json
        }

        val services = buildList {
            if (!fields.containsKey("operations")) {
                params.reportError("Missing 'operations' field")
                return@buildList
            }

            if (fields["operations"] !is ObjectNode) {
                params.reportError("Invalid value for 'operations', expected object")
                return@buildList
            }

            val operationsConfig = parseObjectNode(fields["operations"] as ObjectNode)
            for ((serviceName, operationsField) in operationsConfig) {
                if (operationsField !is ObjectNode) {
                    params.reportError("Invalid value for '$serviceName', expected object")
                    continue
                }

                val operationsConfig = parseObjectNode(operationsField as ObjectNode)

                val operations = buildList {
                    for ((operationName, operationConfig) in operationsConfig) {
                        if (operationConfig !is StringNode) {
                            params.reportError("Invalid value for operation config for '$operationName', expected string")
                            continue
                        }

                        val words = operationConfig.value.split(" ")
                        if (words.size < 2) {
                            params.reportError("Invalid operation config for '$operationName', expected '<method> <path> <parameters>'")
                            continue
                        }

                        val methodEnum = when (val methodName = words[0]) {
                            "GET" -> HttpTransportConfiguration.HttpMethod.Get
                            "POST" -> HttpTransportConfiguration.HttpMethod.Post
                            "PUT" -> HttpTransportConfiguration.HttpMethod.Put
                            "DELETE" -> HttpTransportConfiguration.HttpMethod.Delete
                            "PATCH" -> HttpTransportConfiguration.HttpMethod.Patch
                            else -> {
                                params.reportError("Invalid http method '$methodName'")
                                continue
                            }
                        }

                        val path = words[1]
                        val parameterConfigParts = words.drop(2)
                        val parameters = buildList {

                            // parse path and path parameters
                            val pathComponents = path.split("/")
                            for (component in pathComponents) {
                                if (component.startsWith("{") && component.endsWith("}")) {
                                    val pathParameterName = component.substring(1, component.length - 1)

                                    if (pathParameterName.isEmpty()) {
                                        params.reportError("Expected parameter name between curly braces in '$path'")
                                        continue
                                    }

                                    add(HttpTransportConfiguration.ParameterConfiguration(
                                        name = pathParameterName,
                                        transportMode = HttpTransportConfiguration.TransportMode.Path,
                                    ))
                                }
                            }

                            // parse parameter declarations
                            for (component in parameterConfigParts) {
                                if (component.startsWith("{") && component.endsWith("}")) {
                                    val parameterConfig = component.substring(1, component.length - 1)
                                    if (parameterConfig.isEmpty()) {
                                        params.reportError("Expected parameter name between curly braces in '$path'")
                                        continue
                                    }

                                    val parts = parameterConfig.split(":")
                                    if (parts.size != 2) {
                                        params.reportError("Expected parameter in format '{type:name}', got '$component'")
                                        continue
                                    }

                                    val (type, name) = parts
                                    val transportMode = when (type) {
                                        "query" -> HttpTransportConfiguration.TransportMode.Query
                                        "header" -> HttpTransportConfiguration.TransportMode.Header
                                        "body" -> HttpTransportConfiguration.TransportMode.Body
                                        "cookie" -> HttpTransportConfiguration.TransportMode.Cookie
                                        else -> {
                                            params.reportError("Invalid transport mode '$type'")
                                            continue
                                        }
                                    }

                                    add(HttpTransportConfiguration.ParameterConfiguration(
                                        name = name,
                                        transportMode = transportMode,
                                    ))
                                } else {
                                    params.reportError("Expected parameter in format '{type:name}', got '$component'")
                                }
                            }
                        }

                        add(HttpTransportConfiguration.OperationConfiguration(
                            name = operationName,
                            method = methodEnum,
                            path = path,
                            parameters = parameters,
                        ))
                    }
                }

                add(HttpTransportConfiguration.ServiceConfiguration(
                    name = serviceName,
                    operations = operations,
                ))
            }
        }

        // TODO: implement faults parsing

        return HttpTransportConfiguration(
            serializationMode = serializationMode,
            services = services,
        )
    }

    private fun parseObjectNode(node: ObjectNode): Map<String, ExpressionNode> {
        val result = mutableMapOf<String, ExpressionNode>()
        for (field in node.fields) {
            result[field.name.name] = field.value
        }
        return result
    }
}

// TODO: store fault config
class HttpTransportConfiguration(
    val serializationMode: SerializationMode,
    val services: List<ServiceConfiguration>,
) : TransportConfiguration {
    class ServiceConfiguration(
        val name: String,
        val operations: List<OperationConfiguration>
    ) {
        fun getOperation(name: String): OperationConfiguration? {
            return operations.firstOrNull { it.name == name }
        }
    }

    class OperationConfiguration(
        val name: String,
        val method: HttpMethod,
        val path: String,
        val parameters: List<ParameterConfiguration>,
    ) {
        fun getParameter(name: String): ParameterConfiguration? {
            return parameters.firstOrNull { it.name == name }
        }
    }

    class ParameterConfiguration(
        val name: String,
        val transportMode: TransportMode,
    )

    enum class SerializationMode {
        Json,
    }

    enum class TransportMode {
        Body,       // encoded in request body via serializationMode
        Query,      // encoded as url query parameter
        Path,       // encoded as part of url path
        Header,     // encoded as HTTP header
        Cookie,     // encoded as HTTP cookie
    }

    enum class HttpMethod {
        Get,
        Post,
        Put,
        Delete,
        Patch,
    }

    fun getService(name: String): ServiceConfiguration? {
        return services.firstOrNull { it.name == name }
    }

    fun getMethod(serviceName: String, operationName: String): HttpMethod {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        return operation?.method ?: HttpMethod.Post
    }

    fun getPath(serviceName: String, operationName: String): String {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        return operation?.path ?: "/$operationName"
    }

    fun getTransportMode(serviceName: String, operationName: String, parameterName: String): TransportMode {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        val parameter = operation?.getParameter(parameterName)
        return parameter?.transportMode ?: TransportMode.Body
    }
}
