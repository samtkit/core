package tools.samt.codegen

interface GeneratorParams {
    val packages: List<SamtPackage>

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
    val identifier: String
    fun generate(generatorParams: GeneratorParams): List<CodegenFile>
}

interface TransportConfigurationParser {
    val transportName: String
    fun default(): TransportConfiguration
    fun parse(configuration: Map<String, Any>): TransportConfiguration
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
    /** The type this alias stands for, could be another alias */
    val aliasedType: TypeReference

    /** The fully resolved type, will not contain any type aliases anymore, just the underlying merged type */
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
    val service: TypeReference
    val operations: List<ServiceOperation>
}

interface ConsumerType : Type {
    val provider: ProviderType
    val uses: List<ConsumerUses>
    val targetPackage: String
}

interface ConsumerUses {
    val service: TypeReference
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
