package tools.samt.api.types

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
 * An ordered list of elements
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
