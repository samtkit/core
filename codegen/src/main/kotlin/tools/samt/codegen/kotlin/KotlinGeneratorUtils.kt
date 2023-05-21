package tools.samt.codegen.kotlin

import tools.samt.codegen.*

object KotlinGeneratorConfig {
    const val removePrefixFromSamtPackage = "removePrefixFromSamtPackage"
    const val addPrefixToKotlinPackage = "addPrefixToKotlinPackage"
}

const val GeneratedFilePreamble = """@file:Suppress("RemoveRedundantQualifierName", "unused", "UnusedImport", "LocalVariableName", "FunctionName", "ConvertTwoComparisonsToRangeCheck", "ReplaceSizeCheckWithIsNotEmpty")"""

internal fun String.replacePackage(options: Map<String, String>): String {
    val removePrefix = options[KotlinGeneratorConfig.removePrefixFromSamtPackage]
    val addPrefix = options[KotlinGeneratorConfig.addPrefixToKotlinPackage]

    var result = this

    if (removePrefix != null) {
        result = result.removePrefix(removePrefix).removePrefix(".")
    }

    if (addPrefix != null) {
        result = "$addPrefix.$result"
    }

    return result
}

internal fun SamtPackage.getQualifiedName(options: Map<String, String>): String = qualifiedName.replacePackage(options)

internal fun TypeReference.getQualifiedName(options: Map<String, String>): String {
    val qualifiedName = type.getQualifiedName(options)
    return if (isOptional) {
        "$qualifiedName?"
    } else {
        qualifiedName
    }
}

internal fun Type.getQualifiedName(options: Map<String, String>): String = when (this) {
    is LiteralType -> when (this) {
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
        else -> error("Unsupported literal type: ${this.javaClass.simpleName}")
    }

    is ListType -> "List<${elementType.getQualifiedName(options)}>"
    is MapType -> "Map<${keyType.getQualifiedName(options)}, ${valueType.getQualifiedName(options)}>"

    is UserType -> qualifiedName.replacePackage(options)

    else -> error("Unsupported type: ${javaClass.simpleName}")
}
