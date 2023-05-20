package tools.samt.semantic

import tools.samt.parser.*

sealed interface Type {
    val humanReadableName: String
}

class PackageType(val sourcePackage: Package) : Type {
    val packageName: String get() = sourcePackage.name
    override val humanReadableName: String = packageName
}

/**
 * A type that could not be parsed correctly
 */
object UnknownType : Type {
    override val humanReadableName: String = "Unknown"
}

sealed interface LiteralType : Type

sealed interface NumberType : Type

/**
 * 32-bit whole number, signed
 */
object IntType : LiteralType, NumberType {
    override val humanReadableName: String
        get() = "Int"
}

/**
 * 64-bit whole number, signed
 */
object LongType : LiteralType, NumberType {
    override val humanReadableName: String
        get() = "Long"
}

/**
 * 32-bit floating point number, signed
 */
object FloatType : LiteralType, NumberType {
    override val humanReadableName: String
        get() = "Float"
}

/**
 * 64-bit floating point number, signed
 */
object DoubleType : LiteralType, NumberType {
    override val humanReadableName: String
        get() = "Double"
}

/**
 * Arbitrary precision number, fixed amount of digits before and after decimal point
 */
object DecimalType : LiteralType, NumberType {
    override val humanReadableName: String
        get() = "Decimal"
}

/**
 * Can be true or false
 */
object BooleanType : LiteralType {
    override val humanReadableName: String
        get() = "Boolean"
}

/**
 * UTF-8 encoded text
 */
object StringType : LiteralType {
    override val humanReadableName: String
        get() = "String"
}

/**
 * Arbitrary binary data
 */
object BytesType : LiteralType {
    override val humanReadableName: String
        get() = "Bytes"
}

/**
 * A date without a time
 */
object DateType : LiteralType {
    override val humanReadableName: String
        get() = "Date"
}

/**
 * A date with a time, defaults to millisecond precision
 */
object DateTimeType : LiteralType {
    override val humanReadableName: String
        get() = "DateTime"
}

/**
 * Time duration, millisecond precision
 */
object DurationType : LiteralType {
    override val humanReadableName: String
        get() = "Duration"
}

sealed interface CompoundType : Type

sealed interface UserDeclared {
    val declaration: Node
}

data class ListType(
    val elementType: TypeReference,
    val node: GenericSpecializationNode,
) : CompoundType {
    override val humanReadableName: String = "List<${elementType.humanReadableName}>"
}

data class MapType(
    val keyType: TypeReference,
    val valueType: TypeReference,
    val node: GenericSpecializationNode,
) : CompoundType {
    override val humanReadableName: String = "Map<${keyType.humanReadableName}, ${valueType.humanReadableName}>"
}

class AliasType(
    val name: String,
    /** The type this alias stands for, could be another alias */
    var aliasedType: TypeReference,
    /** The fully resolved type, will not contain any type aliases anymore, just the underlying merged type */
    var fullyResolvedType: ResolvedTypeReference? = null,
    override val declaration: TypeAliasNode,
) : CompoundType, UserDeclared {
    override val humanReadableName: String = name
}

class RecordType(
    val name: String,
    val fields: List<Field>,
    override val declaration: RecordDeclarationNode,
) : CompoundType, UserDeclared {
    class Field(
        val name: String,
        var type: TypeReference,
        override val declaration: RecordFieldNode,
    ) : UserDeclared

    override val humanReadableName: String = name
}

class EnumType(
    val name: String,
    val values: List<String>,
    override val declaration: EnumDeclarationNode,
) : CompoundType, UserDeclared {
    override val humanReadableName: String = name
}

class ServiceType(
    val name: String,
    val operations: List<Operation>,
    override val declaration: ServiceDeclarationNode,
) : CompoundType, UserDeclared {
    sealed interface Operation : UserDeclared {
        val name: String
        val parameters: List<Parameter>
        override val declaration: OperationNode
        class Parameter(
            val name: String,
            var type: TypeReference,
            override val declaration: OperationParameterNode,
        ): UserDeclared
    }

    class RequestResponseOperation(
        override val name: String,
        override val parameters: List<Operation.Parameter>,
        override val declaration: RequestResponseOperationNode,
        var returnType: TypeReference?,
        var raisesTypes: List<TypeReference>,
        val isAsync: Boolean,
    ) : Operation

    class OnewayOperation(
        override val name: String,
        override val parameters: List<Operation.Parameter>,
        override val declaration: OnewayOperationNode,
    ) : Operation

    override val humanReadableName: String = name
}

class ProviderType(
    val name: String,
    val implements: List<Implements>,
    @Suppress("unused") val transport: Transport,
    override val declaration: ProviderDeclarationNode,
) : CompoundType, UserDeclared {
    class Implements(
        var service: TypeReference,
        var operations: List<ServiceType.Operation>,
        val node: ProviderImplementsNode,
    )

    class Transport(
        val name: String,
        @Suppress("unused") val configuration: Any?,
    )

    override val humanReadableName: String = name
}

class ConsumerType(
    var provider: TypeReference,
    var uses: List<Uses>,
    override val declaration: ConsumerDeclarationNode,
) : CompoundType, UserDeclared {
    class Uses(
        var service: TypeReference,
        var operations: List<ServiceType.Operation>,
        val node: ConsumerUsesNode,
    )

    override val humanReadableName: String = "consumer for ${provider.humanReadableName}"
}

sealed interface TypeReference {
    val humanReadableName: String
}

data class UnresolvedTypeReference(
    val expression: ExpressionNode,
) : TypeReference {
    override val humanReadableName: String
        get() = "<Unresolved Type at ${expression.location}>"
}

data class ResolvedTypeReference(
    val type: Type,
    /** Includes only the type reference, e.g. "foo.bar.Baz", "Map" or "String" */
    val typeNode: ExpressionNode,
    /** Includes the full type reference, e.g. "List<String>? (1..100)" */
    val fullNode: ExpressionNode = typeNode,
    val isOptional: Boolean = false,
    val constraints: List<Constraint> = emptyList(),
) : TypeReference {
    override val humanReadableName: String
        get() = buildString {
            append(type.humanReadableName)

            if (isOptional) {
                append('?')
            }

            if (constraints.isNotEmpty()) {
                append(' ')
                append('(')
                constraints.joinTo(this, ", ") { it.humanReadableName }
                append(')')
            }
        }

    sealed interface Constraint {
        val humanReadableName: String
        val node: ExpressionNode

        data class Range(
            override val node: ExpressionNode,
            val lowerBound: Number?,
            val upperBound: Number?,
        ) : Constraint {
            override val humanReadableName: String
                get() = "range(${lowerBound ?: '*'}..${upperBound ?: '*'})"
        }

        data class Size(
            override val node: ExpressionNode,
            val lowerBound: Long?,
            val upperBound: Long?,
        ) : Constraint {
            override val humanReadableName: String
                get() = "size(${lowerBound ?: '*'}..${upperBound ?: '*'})"
        }

        data class Pattern(
            override val node: ExpressionNode,
            val pattern: String,
        ) : Constraint {
            override val humanReadableName: String
                get() = "pattern($pattern)"
        }

        data class Value(
            override val node: ExpressionNode,
            val value: Any,
        ) : Constraint {
            override val humanReadableName: String
                get() = "value($value)"
        }
    }
}
