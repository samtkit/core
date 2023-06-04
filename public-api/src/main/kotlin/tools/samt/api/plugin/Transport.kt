package tools.samt.api.plugin

import tools.samt.api.types.ServiceOperation
import tools.samt.api.types.ServiceType

/**
 * A transport configuration parser.
 * This interface is intended to be implemented by a transport configuration parser, for example HTTP.
 * It is used to parse the configuration body into a specific [TransportConfiguration].
 */
interface TransportConfigurationParser {
    /**
     * The name of the transport, used to identify it in the configuration
     */
    val transportName: String

    /**
     * Parses the configuration and returns the configuration object
     * @throws RuntimeException if the configuration is invalid and graceful error handling is not possible
     * @return Parsed configuration
     */
    fun parse(params: TransportConfigurationParserParams): TransportConfiguration
}

/**
 * A base interface for transport configurations.
 * This interface is intended to be sub-typed and extended by transport configuration implementations.
 */
interface TransportConfiguration

/**
 * The parameters for a [TransportConfigurationParser].
 */
interface TransportConfigurationParserParams {
    /**
     * The configuration body to parse, or null if no configuration body was specified
     */
    val config: ConfigurationObject?

    /**
     * Report an error
     * @param message The error message
     * @param context The configuration element that caused the error, will be highlighted in the editor
     */
    fun reportError(message: String, context: ConfigurationElement? = null)

    /**
     * Report a warning
     * @param message The warning message
     * @param context The configuration element that caused the warning, will be highlighted in the editor
     */
    fun reportWarning(message: String, context: ConfigurationElement? = null)

    /**
     * Report an info message
     * @param message The info message
     * @param context The configuration element that caused the info message, will be highlighted in the editor
     */
    fun reportInfo(message: String, context: ConfigurationElement? = null)
}

/**
 * A configuration element
 */
interface ConfigurationElement {
    /**
     * This element as an [ConfigurationObject]
     * @throws RuntimeException if this element is not an object
     */
    val asObject: ConfigurationObject

    /**
     * This element as an [ConfigurationValue]
     * @throws RuntimeException if this element is not a primitive value
     */
    val asValue: ConfigurationValue

    /**
     * This element as an [ConfigurationList]
     * @throws RuntimeException if this element is not a list
     */
    val asList: ConfigurationList
}

/**
 * A configuration object, contains a map of fields
 */
interface ConfigurationObject : ConfigurationElement {
    /**
     * The fields of this object
     */
    val fields: Map<ConfigurationValue, ConfigurationElement>

    /**
     * Get a field by name
     * @throws RuntimeException if the field does not exist
     */
    fun getField(name: String): ConfigurationElement

    /**
     * Get a field by name, or null if it does not exist
     */
    fun getFieldOrNull(name: String): ConfigurationElement?
}

/**
 * A configuration list, contains a list of elements
 */
interface ConfigurationList : ConfigurationElement {
    /**
     * The entries of this list
     */
    val entries: List<ConfigurationElement>
}

/**
 * A primitive configuration value
 */
interface ConfigurationValue : ConfigurationElement {
    /**
     * This value as a string
     * @throws RuntimeException if this value is not a string
     */
    val asString: String

    /**
     * This value as an identifier
     * @throws RuntimeException if this value is not an identifier
     */
    val asIdentifier: String

    /**
     * This value as an enum, matches the enum value by name case-insensitively (e.g. "get" matches HttpMethod.GET)
     * @throws RuntimeException if this value is not convertible to the provided [enum]
     */
    fun <T : Enum<T>> asEnum(enum: Class<T>): T

    /**
     * This value as a long
     * @throws RuntimeException if this value is not a long
     */
    val asLong: Long

    /**
     * This value as a double
     * @throws RuntimeException if this value is not a double
     */
    val asDouble: Double

    /**
     * This value as a boolean
     * @throws RuntimeException if this value is not a boolean
     */
    val asBoolean: Boolean

    /**
     * This value as a service name, matches against services in the current provider context
     * @throws RuntimeException if this value is not a service within the current provider context
     */
    val asServiceName: ServiceType

    /**
     * This value as a service operation name, matches against operations in the current provider context and [service]
     * @throws RuntimeException if this value is not a service operation within the current provider context and [service]
     */
    fun asOperationName(service: ServiceType): ServiceOperation
}

/**
 * Convenience wrapper for [ConfigurationValue.asEnum]
 */
inline fun <reified T : Enum<T>> ConfigurationValue.asEnum() = asEnum(T::class.java)
