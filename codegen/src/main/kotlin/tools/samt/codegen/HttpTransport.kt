package tools.samt.codegen

class HttpTransportConfigurationParser : TransportConfigurationParser {
    override val transportName: String
        get() = "http"

    override fun default(): TransportConfiguration = HttpTransportConfiguration()

    override fun parse(configuration: Map<String, Any>): HttpTransportConfiguration {
        return HttpTransportConfiguration()
    }
}

class HttpTransportConfiguration : TransportConfiguration {
    enum class TransportMode {
        Body,
        Query,
        Path,
        Header,
        Cookie,
    }
    enum class HttpMethod {
        Get,
        Post,
        Put,
        Delete,
        Patch,
    }
    fun getMethod(operation: ServiceOperation): HttpMethod {
        return HttpMethod.Post
    }
    fun getPath(operation: ServiceOperation): String {
        return "/todo"
    }
    fun getTransportMode(parameter: ServiceOperationParameter): TransportMode {
        return TransportMode.Body
    }
}
