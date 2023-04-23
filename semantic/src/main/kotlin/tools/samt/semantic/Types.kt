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

/**
 * 32-bit whole number, signed
 */
object IntType : LiteralType {
    override val humanReadableName: String
        get() = "Int"
}

/**
 * 64-bit whole number, signed
 */
object LongType : LiteralType {
    override val humanReadableName: String
        get() = "Long"
}

/**
 * 32-bit floating point number, signed
 */
object FloatType : LiteralType {
    override val humanReadableName: String
        get() = "Float"
}

/**
 * 64-bit floating point number, signed
 */
object DoubleType : LiteralType {
    override val humanReadableName: String
        get() = "Double"
}

/**
 * Arbitrary precision number, fixed amount of digits before and after decimal point
 */
object DecimalType : LiteralType {
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

sealed interface UserDefinedType : Type {
    val definition: Node
}

data class ListType(
    val elementType: TypeReference,
) : CompoundType {
    override val humanReadableName: String = "List<${elementType.humanReadableName}>"
}

data class MapType(
    val keyType: TypeReference,
    val valueType: TypeReference,
) : CompoundType {
    override val humanReadableName: String = "Map<${keyType.humanReadableName}, ${valueType.humanReadableName}>"
}

data class RecordType(
    val name: String,
    val fields: List<Field>,
    override val definition: RecordDeclarationNode,
) : CompoundType, UserDefinedType {
    data class Field(
        val name: String,
        var type: TypeReference,
    )

    override val humanReadableName: String = name
}

data class EnumType(
    val name: String,
    val values: List<String>,
    override val definition: EnumDeclarationNode,
) : CompoundType, UserDefinedType {
    override val humanReadableName: String = name
}

data class ServiceType(
    val name: String,
    val operation: List<Operation>,
    override val definition: ServiceDeclarationNode,
) : CompoundType, UserDefinedType {
    sealed class Operation(
        val name: String,
        val parameters: List<Parameter>,
    ) {
        data class Parameter(
            val name: String,
            var type: TypeReference,
        )
    }

    class RequestResponseOperation(
        name: String,
        parameters: List<Parameter>,
        var returnType: TypeReference?,
        var raisesTypes: List<TypeReference>,
    ) : Operation(name, parameters)

    class OnewayOperation(
        name: String,
        parameters: List<Parameter>,
    ) : Operation(name, parameters)

    override val humanReadableName: String = name
}

data class ProviderType(
    val name: String,
    val implements: List<Implements>,
    val transport: Transport,
    override val definition: ProviderDeclarationNode,
) : CompoundType, UserDefinedType {
    data class Implements(
        var service: TypeReference,
        var operations: List<ServiceType.Operation>,
    )

    data class Transport(
        val name: String,
        val configuration: Any?,
    )

    override val humanReadableName: String = name
}

data class ConsumerType(
    var provider: TypeReference,
    var uses: List<Uses>,
    override val definition: ConsumerDeclarationNode,
) : CompoundType, UserDefinedType {
    data class Uses(
        val service: TypeReference,
        var operations: List<ServiceType.Operation>,
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

        data class Range(
            val lowerBound: Number?,
            val upperBound: Number?,
        ) : Constraint {
            override val humanReadableName: String
                get() = "range(${lowerBound ?: '*'}..${upperBound ?: '*'})"
        }

        data class Size(
            val lowerBound: Long?,
            val upperBound: Long?,
        ) : Constraint {
            override val humanReadableName: String
                get() = "size(${lowerBound ?: '*'}..${upperBound ?: '*'})"
        }

        data class Pattern(
            val pattern: String,
        ) : Constraint {
            override val humanReadableName: String
                get() = "pattern($pattern)"
        }

        data class Value(
            val value: Any,
        ) : Constraint {
            override val humanReadableName: String
                get() = "value($value)"
        }
    }
}
