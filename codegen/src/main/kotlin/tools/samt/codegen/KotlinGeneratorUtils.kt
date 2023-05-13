package tools.samt.codegen

internal val TypeReference.qualifiedName: String
    get() = buildString {
        val qualifiedName = when (val type = type) {
            is LiteralType -> when (type) {
                is StringType -> "String"
                is BytesType -> "ByteArray"
                is IntType -> "Int"
                is LongType -> "Long"
                is FloatType -> "Float"
                is DoubleType -> "Double"
                is DecimalType -> "java.math.BigDecimal"
                is BooleanType -> "Boolean"
                is DateType -> "java.time.LocalDate"
                is DateTimeType -> "java.time.LocalDateTime"
                is DurationType -> "java.time.Duration"
                else -> error("Unsupported literal type: ${type.javaClass.simpleName}")
            }

            is ListType -> "List<${type.elementType.qualifiedName}>"
            is MapType -> "Map<${type.keyType.qualifiedName}, ${type.valueType.qualifiedName}>"

            is UserType -> type.qualifiedName // TODO: consider configurable package name

            else -> error("Unsupported type: ${type.javaClass.simpleName}")
        }
        append(qualifiedName)

        if (isOptional) {
            append("?")
        }
    }
