package tools.samt.api.transports.http

/** HTTP Method is used for a given operation */
enum class HttpMethod {
    /** HTTP GET */
    Get,

    /** HTTP POST */
    Post,

    /** HTTP PUT */
    Put,

    /** HTTP DELETE */
    Delete,

    /** HTTP PATCH */
    Patch,
}
