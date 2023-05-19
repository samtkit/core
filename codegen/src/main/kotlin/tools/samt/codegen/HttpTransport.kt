package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.parser.*
import tools.samt.semantic.Package

// TODO: refactor diagnostic controller support

class HttpTransportConfigurationParser: TransportConfigurationParser {
    override val transportName: String
        get() = "http"

    override fun default(): TransportConfiguration = HttpTransportConfiguration(
        serializationMode = HttpTransportConfiguration.SerializationMode.Json,
        services = emptyList(),
        exceptionMap = emptyMap(),
    )

    class Params(
        override val configObjectNode: ObjectNode,
        val controller: DiagnosticController
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

    override fun parse(params: TransportConfigurationParserParams): HttpTransportConfiguration {
        require(params is Params) { "Invalid params type" }

        val fields = parseObjectNode(params.configObjectNode)

        val serializationMode = if (fields.containsKey("serialization")) {
            val serializationConfig = fields["serialization"]!!
            if (serializationConfig is StringNode) {
                when (serializationConfig.value) {
                    "json" -> HttpTransportConfiguration.SerializationMode.Json
                    else -> {
                        // unknown serialization mode
                        serializationConfig.reportError(params.controller) {
                            message("Unknown serialization mode '${serializationConfig.value}', defaulting to 'json'")
                            highlight(serializationConfig.location, "unknown serialization mode")
                        }
                        HttpTransportConfiguration.SerializationMode.Json
                    }
                }
            } else {
                // invalid serialization mode type, expected string
                serializationConfig.reportError(params.controller) {
                    message("Expected serialization config option to be a string, defaulting to 'json'")
                    highlight(serializationConfig.location, "")
                }
                HttpTransportConfiguration.SerializationMode.Json
            }
        } else {
            HttpTransportConfiguration.SerializationMode.Json
        }

        val services = buildList {
            if (!fields.containsKey("operations")) {
                return@buildList
            }

            if (fields["operations"] !is ObjectNode) {
                fields["operations"]!!.reportError(params.controller) {
                    message("Invalid value for 'operations', expected object")
                    highlight(fields["operations"]!!.location)
                }
                return@buildList
            }

            val operationsConfig = parseObjectNode(fields["operations"] as ObjectNode)
            for ((serviceName, operationsField) in operationsConfig) {
                if (operationsField !is ObjectNode) {
                    operationsField.reportError(params.controller) {
                        message("Invalid value for '$serviceName', expected object")
                        highlight(operationsField.location)
                    }
                    continue
                }

                val operationsConfig = parseObjectNode(operationsField as ObjectNode)

                val operations = buildList {
                    for ((operationName, operationConfig) in operationsConfig) {
                        if (operationConfig !is StringNode) {
                            operationConfig.reportError(params.controller) {
                                message("Invalid value for operation config for '$operationName', expected string")
                                highlight(operationConfig.location)
                            }
                            continue
                        }

                        val words = operationConfig.value.split(" ")
                        if (words.size < 2) {
                            operationConfig.reportError(params.controller) {
                                message("Invalid operation config for '$operationName', expected '<method> <path> <parameters>'")
                                highlight(operationConfig.location)
                            }
                            continue
                        }

                        val methodEnum = when (val methodName = words[0]) {
                            "GET" -> HttpTransportConfiguration.HttpMethod.Get
                            "POST" -> HttpTransportConfiguration.HttpMethod.Post
                            "PUT" -> HttpTransportConfiguration.HttpMethod.Put
                            "DELETE" -> HttpTransportConfiguration.HttpMethod.Delete
                            "PATCH" -> HttpTransportConfiguration.HttpMethod.Patch
                            else -> {
                                operationConfig.reportError(params.controller) {
                                    message("Invalid http method '$methodName'")
                                    highlight(operationConfig.location)
                                }
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
                                        operationConfig.reportError(params.controller) {
                                            message("Expected parameter name between curly braces in '$path'")
                                            highlight(operationConfig.location)
                                        }
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
                                        operationConfig.reportError(params.controller) {
                                            message("Expected parameter name between curly braces in '$path'")
                                            highlight(operationConfig.location)
                                        }
                                        continue
                                    }

                                    val parts = parameterConfig.split(":")
                                    if (parts.size != 2) {
                                        operationConfig.reportError(params.controller) {
                                            message("Expected parameter in format '{type:name}', got '$component'")
                                            highlight(operationConfig.location)
                                        }
                                        continue
                                    }

                                    val (type, name) = parts
                                    val transportMode = when (type) {
                                        "query" -> HttpTransportConfiguration.TransportMode.Query
                                        "header" -> HttpTransportConfiguration.TransportMode.Header
                                        "body" -> HttpTransportConfiguration.TransportMode.Body
                                        "cookie" -> HttpTransportConfiguration.TransportMode.Cookie
                                        else -> {
                                            operationConfig.reportError(params.controller) {
                                                message("Invalid transport mode '$type'")
                                                highlight(operationConfig.location)
                                            }
                                            continue
                                        }
                                    }

                                    add(HttpTransportConfiguration.ParameterConfiguration(
                                        name = name,
                                        transportMode = transportMode,
                                    ))
                                } else {
                                    operationConfig.reportError(params.controller) {
                                        message("Expected parameter in format '{type:name}', got '$component'")
                                        highlight(operationConfig.location)
                                    }
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

        val exceptions = buildMap {
            if (fields.containsKey("faults")) {
                if (fields["faults"] is ObjectNode) {
                    val faultMapping = parseObjectNode(fields["faults"] as ObjectNode)
                    for ((faultName, statusCode) in faultMapping) {

                        if (statusCode !is IntegerNode) {
                            statusCode.reportError(params.controller) {
                                message("Expected integer value for '$faultName' fault status code")
                                highlight(statusCode.location)
                            }
                            continue
                        }

                        set(faultName, (statusCode as IntegerNode).value)
                    }
                }
            }
        }

        return HttpTransportConfiguration(
            serializationMode = serializationMode,
            services = services,
            exceptionMap = exceptions,
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
    val exceptionMap: Map<String, Long>,
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
