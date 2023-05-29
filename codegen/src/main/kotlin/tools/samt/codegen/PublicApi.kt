package tools.samt.codegen

interface GeneratorParams {
    val packages: List<SamtPackage>
    val options: Map<String, String>

    fun reportError(message: String)
    fun reportWarning(message: String)
    fun reportInfo(message: String)
}

interface SamtPackage {
    val name: String
    val qualifiedName: String
    val records: List<RecordType>
    val enums: List<EnumType>
    val services: List<ServiceType>
    val providers: List<ProviderType>
    val consumers: List<ConsumerType>
    val aliases: List<AliasType>
}

interface Generator {
    val name: String
    fun generate(generatorParams: GeneratorParams): List<CodegenFile>
}

interface TransportConfigurationParserParams {
    val config: ConfigurationObject

    fun reportError(message: String, context: ConfigurationElement? = null)
    fun reportWarning(message: String, context: ConfigurationElement? = null)
    fun reportInfo(message: String, context: ConfigurationElement? = null)
}

interface ConfigurationElement {
    val asObject: ConfigurationObject
    val asValue: ConfigurationValue
    val asList: ConfigurationList
}

interface ConfigurationObject : ConfigurationElement {
    val fields: Map<ConfigurationValue, ConfigurationElement>
    fun getField(name: String): ConfigurationElement
    fun getFieldOrNull(name: String): ConfigurationElement?
}

interface ConfigurationList : ConfigurationElement {
    val entries: List<ConfigurationElement>
}

interface ConfigurationValue : ConfigurationElement {
    val asString: String
    val asIdentifier: String
    fun <T : Enum<T>> asEnum(enum: Class<T>): T
    val asLong: Long
    val asDouble: Double
    val asBoolean: Boolean
    val asServiceName: ServiceType
    fun asOperationName(service: ServiceType): ServiceOperation
}

inline fun <reified T : Enum<T>> ConfigurationValue.asEnum() = asEnum(T::class.java)

/**
 * A transport configuration parser.
 * This interface is intended to be implemented by a transport configuration parser, for example HTTP.
 * It is used to parse the configuration body into a specific [TransportConfiguration].
 */
interface TransportConfigurationParser {
    val transportName: String

    /**
     * Create the default configuration for this transport, used when no configuration body is specified
     * @return Default configuration
     */
    fun default(): TransportConfiguration

    /**
     * Parses the configuration body and returns the configuration object
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
 * A SAMT type
 */
interface Type

interface LiteralType : Type

interface IntType : LiteralType
interface LongType : LiteralType
interface FloatType : LiteralType
interface DoubleType : LiteralType
interface DecimalType : LiteralType
interface BooleanType : LiteralType
interface StringType : LiteralType
interface BytesType : LiteralType
interface DateType : LiteralType
interface DateTimeType : LiteralType
interface DurationType : LiteralType

/**
 * A ordered list of elements
 */
interface ListType : Type {
    /**
     * The type of the elements in the list
     */
    val elementType: TypeReference
}

/**
 * A map of key-value pairs
 */
interface MapType : Type {
    /**
     * The type of the keys in the map
     */
    val keyType: TypeReference

    /**
     * The type of the values in the map
     */
    val valueType: TypeReference
}

interface UserType : Type {
    val name: String
    val qualifiedName: String
}

interface AliasType : UserType {
    /**
     * The type this alias stands for, could be another alias
     */
    val aliasedType: TypeReference

    /**
     * The fully resolved type, will not contain any type aliases anymore, just the underlying merged type
     */
    val fullyResolvedType: TypeReference
}

/**
 * A SAMT record
 */
interface RecordType : UserType {
    val fields: List<RecordField>
}

/**
 * A field in a record
 */
interface RecordField {
    val name: String
    val type: TypeReference
}

/**
 * A SAMT enum
 */
interface EnumType : UserType {
    val values: List<String>
}

/**
 * A SAMT service
 */
interface ServiceType : UserType {
    val operations: List<ServiceOperation>
}

/**
 * An operation in a service
 */
interface ServiceOperation {
    val name: String
    val parameters: List<ServiceOperationParameter>
}

/**
 * A parameter in a service operation
 */
interface ServiceOperationParameter {
    val name: String
    val type: TypeReference
}

/**
 * A service operation that returns a response
 */
interface RequestResponseOperation : ServiceOperation {
    /**
     * The return type of this operation.
     * If null, this operation returns nothing.
     */
    val returnType: TypeReference?

    /**
     * Is true if this operation is asynchronous.
     * This could mean that the operation returns a future in Java, or a Promise in JavaScript.
     */
    val isAsync: Boolean
}

/**
 * A service operation that is fire-and-forget, never returning a response
 */
interface OnewayOperation : ServiceOperation

/**
 * A SAMT provider
 */
interface ProviderType : UserType {
    val implements: List<ProvidedService>
    val transport: TransportConfiguration
}

/**
 * Connects a provider to a service
 */
interface ProvidedService {
    /**
     * The underlying service this provider implements
     */
    val service: ServiceType

    /**
     * The operations that are implemented by this provider
     */
    val implementedOperations: List<ServiceOperation>

    /**
     * The operations that are not implemented by this provider
     */
    val unimplementedOperations: List<ServiceOperation>
}

/**
 * A SAMT consumer
 */
interface ConsumerType : Type {
    /**
     * The provider this consumer is connected to
     */
    val provider: ProviderType

    /**
     * The services this consumer uses
     */
    val uses: List<ConsumedService>

    /**
     * The package this consumer is located in
     */
    val samtPackage: String
}

/**
 * Connects a consumer to a service
 */
interface ConsumedService {
    /**
     * The underlying service this consumer uses
     */
    val service: ServiceType

    /**
     * The operations that are consumed by this consumer
     */
    val consumedOperations: List<ServiceOperation>

    /**
     * The operations that are not consumed by this consumer
     */
    val unconsumedOperations: List<ServiceOperation>
}

/**
 * A type reference
 */
interface TypeReference {
    /**
     * The type this reference points to
     */
    val type: Type

    /**
     * Is true if this type reference is optional, meaning it can be null
     */
    val isOptional: Boolean

    /**
     * The range constraints placed on this type, if any
     */
    val rangeConstraint: Constraint.Range?

    /**
     * The size constraints placed on this type, if any
     */
    val sizeConstraint: Constraint.Size?

    /**
     * The pattern constraints placed on this type, if any
     */
    val patternConstraint: Constraint.Pattern?

    /**
     * The value constraints placed on this type, if any
     */
    val valueConstraint: Constraint.Value?

    /**
     * The runtime type this reference points to, could be different from [type] if this is an alias
     */
    val runtimeType: Type

    /**
     * Is true if this type reference or underlying type is optional, meaning it can be null at runtime
     * This is different from [isOptional] in that it will return true for an alias that points to an optional type
     */
    val isRuntimeOptional: Boolean

    /**
     * The runtime range constraints placed on this type, if any.
     * Will differ from [rangeConstraint] if this is an alias
     */
    val runtimeRangeConstraint: Constraint.Range?

    /**
     * The runtime size constraints placed on this type, if any.
     * Will differ from [sizeConstraint] if this is an alias
     */
    val runtimeSizeConstraint: Constraint.Size?

    /**
     * The runtime pattern constraints placed on this type, if any.
     * Will differ from [patternConstraint] if this is an alias
     */
    val runtimePatternConstraint: Constraint.Pattern?

    /**
     * The runtime value constraints placed on this type, if any.
     * Will differ from [valueConstraint] if this is an alias
     */
    val runtimeValueConstraint: Constraint.Value?
}

interface Constraint {
    interface Range : Constraint {
        val lowerBound: Number?
        val upperBound: Number?
    }

    interface Size : Constraint {
        val lowerBound: Long?
        val upperBound: Long?
    }

    interface Pattern : Constraint {
        val pattern: String
    }

    interface Value : Constraint {
        val value: Any
    }
}
