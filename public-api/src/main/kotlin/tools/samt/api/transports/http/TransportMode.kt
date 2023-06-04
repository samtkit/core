package tools.samt.api.transports.http

/** How a given parameter is transported over HTTP */
enum class TransportMode {
    /** encoded in request body via serializationMode */
    Body,

    /** encoded as url query parameter */
    QueryParameter,

    /** encoded as part of url path */
    Path,

    /** encoded as HTTP header */
    Header,

    /** encoded as HTTP cookie */
    Cookie,
}
