package tools.samt.codegen.kotlin.ktor

import tools.samt.codegen.*
import tools.samt.codegen.kotlin.GeneratedFilePreamble
import tools.samt.codegen.kotlin.getQualifiedName
import tools.samt.codegen.kotlin.getTargetPackage

fun mappingFileContent(pack: SamtPackage, options: Map<String, String>) = buildString {
    if (pack.records.isNotEmpty() || pack.enums.isNotEmpty()) {
        appendLine(GeneratedFilePreamble)
        appendLine()
        appendLine("package ${pack.getQualifiedName(options)}")
        appendLine()
        appendLine("import io.ktor.util.*")
        appendLine("import kotlinx.serialization.json.*")
        appendLine()

        pack.records.forEach { record ->
            appendEncodeRecord(record, options)
            appendDecodeRecord(record, options)
            appendLine()
        }

        pack.enums.forEach { enum ->
            appendEncodeEnum(enum, options)
            appendDecodeEnum(enum, options)
            appendLine()
        }
    }
}

private fun StringBuilder.appendEncodeRecord(
    record: RecordType,
    options: Map<String, String>,
) {
    appendLine("/** Encode and validate record ${record.qualifiedName} to JSON */")
    appendLine("fun `encode ${record.name}`(record: ${record.getQualifiedName(options)}?): JsonElement {")
    appendLine("    if (record == null) return JsonNull")
    for (field in record.fields) {
        appendEncodeRecordField(field, options)
    }
    appendLine("    // Create JSON for ${record.qualifiedName}")
    appendLine("    return buildJsonObject {")
    for (field in record.fields) {
        appendLine("        put(\"${field.name}\", `field ${field.name}`)")
    }
    appendLine("    }")
    appendLine("}")
}

private fun StringBuilder.appendDecodeRecord(
    record: RecordType,
    options: Map<String, String>,
) {
    appendLine("/** Decode and validate record ${record.qualifiedName} from JSON */")
    appendLine("fun `decode ${record.name}`(json: JsonElement): ${record.getQualifiedName(options)} {")
    for (field in record.fields) {
        appendDecodeRecordField(field, options)
    }
    appendLine("    // Create record ${record.qualifiedName}")
    appendLine("    return ${record.getQualifiedName(options)}(")
    for (field in record.fields) {
        appendLine("        ${field.name} = `field ${field.name}`,")
    }
    appendLine("    )")
    appendLine("}")
}

private fun StringBuilder.appendEncodeEnum(enum: EnumType, options: Map<String, String>) {
    val enumName = enum.getQualifiedName(options)
    appendLine("/** Encode enum ${enum.qualifiedName} to JSON */")
    appendLine("fun `encode ${enum.name}`(value: ${enumName}?) = when(value) {")
    appendLine("    null -> null")
    enum.values.forEach { value ->
        appendLine("    ${enumName}.${value} -> \"${value}\"")
    }
    appendLine("    ${enumName}.FAILED_TO_PARSE -> error(\"Cannot encode FAILED_TO_PARSE value\")")
    appendLine("}")
}

private fun StringBuilder.appendDecodeEnum(enum: EnumType, options: Map<String, String>) {
    val enumName = enum.getQualifiedName(options)
    appendLine("/** Decode enum ${enum.qualifiedName} from JSON */")
    appendLine("fun `decode ${enum.name}`(json: JsonElement) = when(json.jsonPrimitive.content) {")
    enum.values.forEach { value ->
        appendLine("    \"${value}\" -> ${enumName}.${value}")
    }
    appendLine("    // Value not found in enum ${enum.qualifiedName}")
    appendLine("    else -> ${enumName}.FAILED_TO_PARSE")
    appendLine("}")
}

private fun StringBuilder.appendEncodeRecordField(field: RecordField, options: Map<String, String>) {
    appendLine("    // Encode field ${field.name}")
    appendLine("    val `field ${field.name}` = run {")
    append("        val value = ")
    if (field.type.isOptional) {
        append("record.${field.name} ?: return@run JsonNull")
    } else {
        append("record.${field.name}")
    }
    appendLine()
    appendLine("        ${encodeJsonElement(field.type, options)}")
    appendLine("    }")
}

private fun StringBuilder.appendDecodeRecordField(field: RecordField, options: Map<String, String>) {
    appendLine("    // Decode field ${field.name}")
    appendLine("    val `field ${field.name}` = run {")
    append("        val jsonElement = ")
    if (field.type.isOptional) {
        append("json.jsonObject[\"${field.name}\"] ?: return@run null")
    } else {
        append("json.jsonObject[\"${field.name}\"]!!")
    }
    appendLine()
    appendLine("        ${decodeJsonElement(field.type, options)}")
    appendLine("    }")
}

fun encodeJsonElement(typeReference: TypeReference, options: Map<String, String>, valueName: String = "value"): String =
    when (val type = typeReference.type) {
        is LiteralType -> {
            val getContent = when (type) {
                is StringType,
                is IntType,
                is LongType,
                is FloatType,
                is DoubleType,
                is BooleanType -> valueName
                is BytesType -> "${valueName}.encodeBase64()"
                is DecimalType -> "${valueName}.toPlainString()"
                is DateType,
                is DateTimeType,
                is DurationType -> "${valueName}.toString()"
                else -> error("Unsupported literal type: ${type.javaClass.simpleName}")
            }
            "Json.encodeToJsonElement($getContent${validateLiteralConstraintsSuffix(typeReference)})"
        }

        is ListType -> "${valueName}.map { ${encodeJsonElement(type.elementType, options, valueName = "it")} }"
        is MapType -> "${valueName}.mapValues { ${encodeJsonElement(type.valueType, options, valueName = "it")} }"

        is UserType -> "${type.getTargetPackage(options)}`encode ${type.name}`(${valueName})"

        else -> error("Unsupported type: ${type.javaClass.simpleName}")
    }

fun decodeJsonElement(typeReference: TypeReference, options: Map<String, String>, valueName: String = "jsonElement"): String =
    when (val type = typeReference.type) {
        is LiteralType -> when (type) {
            is StringType -> "${valueName}.jsonPrimitive.content"
            is BytesType -> "${valueName}.jsonPrimitive.content.decodeBase64Bytes()"
            is IntType -> "${valueName}.jsonPrimitive.int"
            is LongType -> "${valueName}.jsonPrimitive.long"
            is FloatType -> "${valueName}.jsonPrimitive.float"
            is DoubleType -> "${valueName}.jsonPrimitive.double"
            is DecimalType -> "${valueName}.jsonPrimitive.content.let { java.math.BigDecimal(it) }"
            is BooleanType -> "${valueName}.jsonPrimitive.boolean"
            is DateType -> "${valueName}.jsonPrimitive.content?.let { java.time.LocalDate.parse(it) }"
            is DateTimeType -> "${valueName}.jsonPrimitive.content?.let { java.time.LocalDateTime.parse(it) }"
            is DurationType -> "${valueName}.jsonPrimitive.content?.let { java.time.Duration.parse(it) }"
            else -> error("Unsupported literal type: ${type.javaClass.simpleName}")
        } + validateLiteralConstraintsSuffix(typeReference)

        is ListType -> "${valueName}.jsonArray.map { ${decodeJsonElement(type.elementType, options, valueName = "it")} }"
        is MapType -> "${valueName}.jsonObject.mapValues { ${decodeJsonElement(type.valueType, options, valueName = "it")} }"

        is UserType -> "${type.getTargetPackage(options)}`decode ${type.name}`(${valueName})"

        else -> error("Unsupported type: ${type.javaClass.simpleName}")
    }

private fun validateLiteralConstraintsSuffix(typeReference: TypeReference): String {
    val conditions = buildList {
        typeReference.rangeConstraint?.let { constraint ->
            constraint.lowerBound?.let {
                add("it >= ${constraint.lowerBound}")
            }
            constraint.upperBound?.let {
                add("it <= ${constraint.upperBound}")
            }
        }
        typeReference.sizeConstraint?.let { constraint ->
            val property = if (typeReference.type is StringType) "length" else "size"
            constraint.lowerBound?.let {
                add("it.${property} >= ${constraint.lowerBound}")
            }
            constraint.upperBound?.let {
                add("it.${property} <= ${constraint.upperBound}")
            }
        }
        typeReference.patternConstraint?.let { constraint ->
            add("it.matches(\"${constraint.pattern}\")")
        }
        typeReference.valueConstraint?.let { constraint ->
            add("it == ${constraint.value})")
        }
    }

    if (conditions.isEmpty()) {
        return ""
    }

    return "${if (typeReference.isOptional) "?.also" else ".also"} { require(${conditions.joinToString(" && ")}) }"
}
