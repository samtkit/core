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

interface  ConfigurationElement {
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
    fun <T : Enum<T>> asEnum(enum: Class<T>): T
    val asLong: Long
    val asDouble: Double
    val asBoolean: Boolean
    val asServiceName: ServiceType
    fun asOperationName(service: ServiceType): ServiceOperation
}

inline fun <reified T : Enum<T>> ConfigurationValue.asEnum() = asEnum(T::class.java)

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

interface TransportConfiguration

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

interface ListType : Type {
    val elementType: TypeReference
}

interface MapType : Type {
    val keyType: TypeReference
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

interface RecordType : UserType {
    val fields: List<RecordField>
}

interface RecordField {
    val name: String
    val type: TypeReference
}

interface EnumType : UserType {
    val values: List<String>
}

interface ServiceType : UserType {
    val operations: List<ServiceOperation>
}
interface ServiceOperation {
    val name: String
    val parameters: List<ServiceOperationParameter>
}

interface ServiceOperationParameter {
    val name: String
    val type: TypeReference
}

interface RequestResponseOperation : ServiceOperation {
    val returnType: TypeReference?
    val raisesTypes: List<TypeReference>
    val isAsync: Boolean
}

interface OnewayOperation : ServiceOperation

interface ProviderType : UserType {
    val implements: List<ProviderImplements>
    val transport: TransportConfiguration
}

interface ProviderImplements {
    val service: ServiceType
    val operations: List<ServiceOperation>
}

interface ConsumerType : Type {
    val provider: ProviderType
    val uses: List<ConsumerUses>
    val targetPackage: String
}

interface ConsumerUses {
    val service: ServiceType
    val operations: List<ServiceOperation>
}

interface TypeReference {
    val type: Type
    val isOptional: Boolean
    val rangeConstraint: Constraint.Range?
    val sizeConstraint: Constraint.Size?
    val patternConstraint: Constraint.Pattern?
    val valueConstraint: Constraint.Value?
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
