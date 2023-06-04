package tools.samt.api.transports.http

import tools.samt.api.plugin.TransportConfiguration

/**
 * A transport configuration for HTTP-based services.
 */
interface SamtHttpTransport : TransportConfiguration {

    val serializationMode: SerializationMode

    /**
     * Get the HTTP method for the given service and operation.
     */
    fun getMethod(serviceName: String, operationName: String): HttpMethod

    /**
     * Get the full path for the given service and operation, essentially joining [getServicePath] and [getPath] with a slash.
     */
    fun getFullPath(serviceName: String, operationName: String): String

    /**
     * Get the path for the given service and operation.
     * The path might contain URL parameters, which are surrounded by curly braces (e.g. /person/{personId}).
     */
    fun getPath(serviceName: String, operationName: String): String

    /**
     * Get the base path for the given service.
     */
    fun getServicePath(serviceName: String): String

    /**
     * Get the transport mode for the given parameter.
     * Defaults to [TransportMode.Body] for POST operations and [TransportMode.QueryParameter] for GET operations.
     */
    fun getTransportMode(serviceName: String, operationName: String, parameterName: String): TransportMode
}
