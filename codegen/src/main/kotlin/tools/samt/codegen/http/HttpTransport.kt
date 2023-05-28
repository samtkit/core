package tools.samt.codegen.http

import tools.samt.codegen.*

object HttpTransportConfigurationParser : TransportConfigurationParser {
    override val transportName: String
        get() = "http"

    override fun default(): TransportConfiguration = HttpTransportConfiguration(
        serializationMode = HttpTransportConfiguration.SerializationMode.Json,
        services = emptyList(),
    )

    override fun parse(params: TransportConfigurationParserParams): HttpTransportConfiguration {
        val config = params.config
        val serializationMode =
            config.getFieldOrNull("serialization")?.asValue?.asEnum<HttpTransportConfiguration.SerializationMode>()
                ?: HttpTransportConfiguration.SerializationMode.Json

        val services = config.getFieldOrNull("operations")?.asObject?.let { operations ->

            operations.asObject.fields.map { (operationsKey, operationsField) ->
                // TODO This currently fails horribly if an operation is called basePath
                val servicePath = operations.getFieldOrNull("basePath")?.asValue?.asString ?: ""
                val service = operationsKey.asServiceName
                val serviceName = service.name
                val operationConfiguration = operationsField.asObject

                val parsedOperations = operationConfiguration.fields.filterKeys { it.asIdentifier != "basePath" }.map { (key, value) ->
                    val operationConfig = value.asValue
                    val operation = key.asOperationName(service)
                    val operationName = operation.name
                    val methodEndpointRegex = Regex("""(\w+)\s+(\S+)(.*)""")
                    val parameterRegex = Regex("""\{(.*?) in (.*?)}""")

                    val methodEndpointResult = methodEndpointRegex.matchEntire(operationConfig.asString)
                    if (methodEndpointResult == null) {
                        params.reportError(
                            "Invalid operation config for '$operationName', expected '<method> <path> <parameters>'",
                            operationConfig
                        )
                        error("Invalid operation config for '$operationName', expected '<method> <path> <parameters>'")
                    }

                    val (method, path, parameterPart) = methodEndpointResult.destructured

                    val methodEnum = when (method) {
                        "GET" -> HttpTransportConfiguration.HttpMethod.Get
                        "POST" -> HttpTransportConfiguration.HttpMethod.Post
                        "PUT" -> HttpTransportConfiguration.HttpMethod.Put
                        "DELETE" -> HttpTransportConfiguration.HttpMethod.Delete
                        "PATCH" -> HttpTransportConfiguration.HttpMethod.Patch
                        else -> {
                            params.reportError("Invalid http method '$method'", operationConfig)
                            error("Invalid http method '$method'")
                        }
                    }

                    val parameters = mutableListOf<HttpTransportConfiguration.ParameterConfiguration>()

                    // parse path and path parameters
                    val pathComponents = path.split("/")
                    for (component in pathComponents) {
                        if (!component.startsWith("{") || !component.endsWith("}")) continue

                        val pathParameterName = component.substring(1, component.length - 1)

                        if (pathParameterName.isEmpty()) {
                            params.reportError(
                                "Expected parameter name between curly braces in '$path'",
                                operationConfig
                            )
                            continue
                        }

                        parameters += HttpTransportConfiguration.ParameterConfiguration(
                            name = pathParameterName,
                            transportMode = HttpTransportConfiguration.TransportMode.Path,
                        )
                    }

                    val parameterResults = parameterRegex.findAll(parameterPart)
                    // parse parameter declarations
                    for (parameterResult in parameterResults) {
                        val (names, type) = parameterResult.destructured
                        val transportMode = when (type) {
                            "query" -> HttpTransportConfiguration.TransportMode.Query
                            "header" -> HttpTransportConfiguration.TransportMode.Header
                            "body" -> HttpTransportConfiguration.TransportMode.Body
                            "cookie" -> HttpTransportConfiguration.TransportMode.Cookie
                            else -> {
                                params.reportError("Invalid transport mode '$type'", operationConfig)
                                continue
                            }
                        }

                        names.split(",").forEach { name ->
                            parameters += HttpTransportConfiguration.ParameterConfiguration(
                                name = name.trim(),
                                transportMode = transportMode,
                            )
                        }
                    }

                    HttpTransportConfiguration.OperationConfiguration(
                        name = operationName,
                        method = methodEnum,
                        path = path,
                        parameters = parameters,
                    )
                }

                HttpTransportConfiguration.ServiceConfiguration(
                    name = serviceName,
                    operations = parsedOperations,
                    path = servicePath
                )
            }
        } ?: emptyList()

        return HttpTransportConfiguration(
            serializationMode = serializationMode,
            services = services,
        )
    }
}

class HttpTransportConfiguration(
    val serializationMode: SerializationMode,
    val services: List<ServiceConfiguration>,
) : TransportConfiguration {
    class ServiceConfiguration(
        val name: String,
        val path: String,
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

    fun getPath(serviceName: String): String {
        val service = getService(serviceName)
        return service?.path ?: ""
    }

    fun getTransportMode(serviceName: String, operationName: String, parameterName: String): TransportMode {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        val parameter = operation?.getParameter(parameterName)
        return parameter?.transportMode ?: TransportMode.Body
    }
}
