package tools.samt.codegen.http

import tools.samt.api.plugin.TransportConfigurationParser
import tools.samt.api.plugin.TransportConfigurationParserParams
import tools.samt.api.plugin.asEnum
import tools.samt.api.transports.http.HttpMethod
import tools.samt.api.transports.http.SamtHttpTransport
import tools.samt.api.transports.http.SerializationMode
import tools.samt.api.transports.http.TransportMode

object HttpTransportConfigurationParser : TransportConfigurationParser {
    override val transportName: String
        get() = "http"

    private val isValidRegex = Regex("""\w+\s+\S+(\s+\{.*?\s+in\s+\S+})*""")
    private val methodEndpointRegex = Regex("""(\w+)\s+(\S+)(.*)""")
    private val parameterRegex = Regex("""\{(.*?)\s+in\s+(\S+)}""")

    override fun parse(params: TransportConfigurationParserParams): HttpTransportConfiguration {
        val config = params.config ?: return HttpTransportConfiguration(
            serializationMode = SerializationMode.Json,
            services = emptyList(),
        )
        val serializationMode =
            config.getFieldOrNull("serialization")?.asValue?.asEnum<SerializationMode>()
                ?: SerializationMode.Json

        val services = config.getFieldOrNull("operations")?.asObject?.let { operations ->
            val parsedServices = mutableListOf<HttpTransportConfiguration.ServiceConfiguration>()

            for ((operationsKey, operationsField) in operations.asObject.fields) {
                val service = operationsKey.asServiceName
                val serviceName = service.name
                val operationConfiguration = operationsField.asObject
                val servicePath = operationConfiguration.getFieldOrNull("basePath")?.asValue?.asString ?: ""

                val operationConfigurations = operationConfiguration.fields.filterKeys { it.asIdentifier != "basePath" }
                val parsedOperations = mutableListOf<HttpTransportConfiguration.OperationConfiguration>()

                operationConfigLoop@ for ((key, value) in operationConfigurations) {
                    val operationConfig = value.asValue
                    val operation = key.asOperationName(service)
                    val operationName = operation.name

                    if (!(operationConfig.asString matches isValidRegex)) {
                        params.reportError(
                            "Invalid operation config for '$operationName', expected '<method> <path> <parameters>'. A valid example: 'POST /${operationName} {parameter1, parameter2 in queryParam}'",
                            operationConfig
                        )
                        continue
                    }

                    val methodEndpointResult = methodEndpointRegex.matchEntire(operationConfig.asString)
                    if (methodEndpointResult == null) {
                        params.reportError(
                            "Invalid operation config for '$operationName', expected '<method> <path> <parameters>'",
                            operationConfig
                        )
                        continue
                    }

                    val (method, path, parameterPart) = methodEndpointResult.destructured

                    val methodEnum = when (method) {
                        "GET" -> HttpMethod.Get
                        "POST" -> HttpMethod.Post
                        "PUT" -> HttpMethod.Put
                        "DELETE" -> HttpMethod.Delete
                        "PATCH" -> HttpMethod.Patch
                        else -> {
                            params.reportError("Invalid http method '$method'", operationConfig)
                            continue
                        }
                    }

                    // FIXME:   This approach has the drawback that it can only detect invalid configuration if they
                    //          are explicitly declared in the config object. If the user implements a method but does
                    //          not provide a configuration for it, it will not be detected as an error.
                    //
                    //          In order to fix this we would need to pass the implemented services and operations to
                    //          the parser and read configurations on demand. That way the parser knows all operations
                    //          and can generate default configurations for operations that have no explicit configuration.

                    // check for duplicate method/path combinations within current service
                    for (parsedOperation in parsedOperations) {
                        if (parsedOperation.path == path && parsedOperation.method == methodEnum) {
                            params.reportError(
                                "Operation '${serviceName}.${operationName}' cannot be mapped to the same method and path combination ($method $servicePath$path) as operation '${serviceName}.${parsedOperation.name}'",
                                operationConfig
                            )
                            continue@operationConfigLoop
                        }
                    }

                    // check for duplicate method/path combinations within previously declared services
                    for (parsedService in parsedServices.filter { it.path == servicePath }) {
                        val duplicate = parsedService.operations.find { op ->
                            op.path == path && op.method == methodEnum
                        }

                        if (duplicate != null) {
                            params.reportError(
                                "Operation '${serviceName}.${operationName}' cannot be mapped to the same method and path combination ($method ${parsedService.path}$path) as operation '${parsedService.name}.${duplicate.name}'",
                                operationConfig
                            )
                            continue@operationConfigLoop
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

                        if (operation.parameters.none { it.name == pathParameterName }) {
                            params.reportError(
                                "Path parameter '$pathParameterName' not found in operation '$operationName'",
                                operationConfig
                            )
                            continue
                        }

                        parameters += HttpTransportConfiguration.ParameterConfiguration(
                            name = pathParameterName,
                            transportMode = TransportMode.Path,
                        )
                    }

                    val parameterResults = parameterRegex.findAll(parameterPart)
                    // parse parameter declarations
                    for (parameterResult in parameterResults) {
                        val (names, type) = parameterResult.destructured
                        val transportMode = when (type) {
                            "query",
                            "queryParam",
                            "queryParams",
                            "queryParameter",
                            "queryParameters",
                            -> TransportMode.QueryParameter

                            "header", "headers" -> TransportMode.Header
                            "body" -> TransportMode.Body
                            "cookie", "cookies" -> TransportMode.Cookie
                            else -> {
                                params.reportError("Invalid transport mode '$type'", operationConfig)
                                continue
                            }
                        }

                        for (name in names.split(",").map { it.trim() }) {
                            if (operation.parameters.none { it.name == name }) {
                                params.reportError(
                                    "Parameter '$name' not found in operation '$operationName'",
                                    operationConfig
                                )
                                continue
                            }

                            if (transportMode == TransportMode.Body && methodEnum == HttpMethod.Get) {
                                params.reportError(
                                    "HTTP GET method doesn't accept '$name' as a BODY parameter",
                                    operationConfig
                                )
                                continue
                            }

                            parameters += HttpTransportConfiguration.ParameterConfiguration(
                                name = name,
                                transportMode = transportMode,
                            )
                        }
                    }

                    parsedOperations += HttpTransportConfiguration.OperationConfiguration(
                        name = operationName,
                        method = methodEnum,
                        path = path,
                        parameters = parameters,
                    )
                }

                parsedServices += HttpTransportConfiguration.ServiceConfiguration(
                    name = serviceName,
                    operations = parsedOperations,
                    path = servicePath
                )
            }

            parsedServices
        } ?: emptyList()

        return HttpTransportConfiguration(
            serializationMode = serializationMode,
            services = services,
        )
    }
}

class HttpTransportConfiguration(
    override val serializationMode: SerializationMode,
    val services: List<ServiceConfiguration>,
) : SamtHttpTransport {
    class ServiceConfiguration(
        val name: String,
        val path: String,
        val operations: List<OperationConfiguration>,
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

    private fun getService(name: String): ServiceConfiguration? {
        return services.firstOrNull { it.name == name }
    }

    override fun getMethod(serviceName: String, operationName: String): HttpMethod {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        return operation?.method ?: HttpMethod.Post
    }

    override fun getPath(serviceName: String, operationName: String): String {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        return operation?.path ?: "/$operationName"
    }

    override fun getFullPath(serviceName: String, operationName: String): String {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        return "${service?.path ?: ""}${operation?.path ?: "/$operationName"}"
    }

    override fun getServicePath(serviceName: String): String {
        val service = getService(serviceName)
        return service?.path ?: ""
    }

    override fun getTransportMode(
        serviceName: String,
        operationName: String,
        parameterName: String,
    ): TransportMode {
        val service = getService(serviceName)
        val operation = service?.getOperation(operationName)
        val parameter = operation?.getParameter(parameterName)
        val mode = parameter?.transportMode

        if (mode != null)
            return mode

        return if (operation?.method == HttpMethod.Get) {
            TransportMode.QueryParameter
        } else {
            TransportMode.Body
        }
    }
}
