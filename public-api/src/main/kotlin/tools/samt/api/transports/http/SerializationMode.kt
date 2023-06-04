package tools.samt.api.transports.http

/** How a given parameter is serialized over HTTP */
enum class SerializationMode {
    /** serialized as JSON */
    Json {
        override fun toString() = "application/json"
    },
}
