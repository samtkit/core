package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.semantic.*

data class CodegenFile(val filepath: String, val source: String)

/*
 * Proof of concept codegen for Kotlin code
 *
 * Todos:
 * - Emit providers
 * - Emit consumers
 * - Modular
 * - Extendable
 * - Configurable
 * */
class Codegen private constructor(
    private val model: Package,
    private val controller: DiagnosticController,
) {
    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generate(): List<CodegenFile> {
        generatePackage(model)
        return emittedFiles
    }

    private fun generatePackage(pack: Package) {

        // root package cannot have any types declared in it, only sub-packages
        if (pack.isRootPackage) {
            check(pack.parent == null)
            check(pack.records.isEmpty())
            check(pack.enums.isEmpty())
            check(pack.aliases.isEmpty())

            check(pack.services.isEmpty())
            check(pack.providers.isEmpty())
            check(pack.consumers.isEmpty())
        } else {
            if (pack.hasModelTypes()) {
                val packageSource = buildString {
                    val packageName = pack.nameComponents.joinToString(".")
                    appendLine("package $packageName")
                    appendLine()

                    pack.records.forEach {
                        appendLine(generateRecord(it))
                    }

                    pack.enums.forEach {
                        appendLine(generateEnum(it))
                    }

                    pack.aliases.forEach {
                        appendLine(generateAlias(it))
                    }

                    pack.services.forEach {
                        appendLine(generateService(it))
                    }
                }

                val filePath = pack.nameComponents.joinToString("/") + ".kt"
                val file = CodegenFile(filePath, packageSource)
                emittedFiles.add(file)
            }

            if (pack.hasProviderTypes()) {


                pack.providers.forEach {
                    val packageSource = buildString {
                        val packageName = pack.nameComponents.joinToString(".")
                        appendLine("package $packageName")
                        appendLine()

                        appendLine(generateProvider(it))
                    }

                    val filePath = pack.nameComponents.joinToString("/") + "_${it.name}" + ".kt"
                    val file = CodegenFile(filePath, packageSource)
                    emittedFiles.add(file)
                }
                val packageSource = buildString {
                    val packageName = pack.nameComponents.joinToString(".")
                    appendLine("package $packageName")
                    appendLine()

                    pack.providers.forEach {
                        appendLine(generateProvider(it))
                    }
                }
            }
        }

        pack.subPackages.forEach { generatePackage(it) }
    }

    private fun generateRecord(record: RecordType): String = buildString {
        appendLine("class ${record.name}(")
        record.fields.forEach { field ->
            val type = field.type as ResolvedTypeReference
            val fullyQualifiedName = generateFullyQualifiedNameForTypeReference(type)
            val isOptional = type.isOptional

            if (isOptional) {
                appendLine("    val ${field.name}: $fullyQualifiedName = null,")
            } else {
                appendLine("    val ${field.name}: $fullyQualifiedName,")
            }
        }
        appendLine(")")
    }

    private fun generateEnum(enum: EnumType): String = buildString {
        appendLine("enum class ${enum.name} {")
        enum.values.forEach {
            appendLine("    ${it},")
        }
        appendLine("}")
    }

    private fun generateAlias(alias: AliasType): String = buildString {
        val fullyQualifiedName = generateFullyQualifiedNameForTypeReference(alias.aliasedType)
        appendLine("typealias ${alias.name} = $fullyQualifiedName")
    }

    private fun generateService(service: ServiceType): String = buildString {
        appendLine("interface ${service.name} {")
        service.operations.forEach { operation ->
            append(generateServiceOperation(operation))
        }
        appendLine("}")
    }

    private fun generateServiceOperation(operation: ServiceType.Operation): String = buildString {
        when (operation) {
            is ServiceType.RequestResponseOperation -> {
                // method head
                if (operation.isAsync) {
                    appendLine("    suspend fun ${operation.name}(")
                } else {
                    appendLine("    fun ${operation.name}(")
                }

                // parameters
                append(generateServiceOperationParameterList(operation.parameters))

                // return type
                if (operation.returnType != null) {
                    val returnType = operation.returnType as ResolvedTypeReference
                    val returnName = generateFullyQualifiedNameForTypeReference(returnType)
                    appendLine("    ): $returnName")
                } else {
                    appendLine("    )")
                }
            }

            is ServiceType.OnewayOperation -> {
                appendLine("    fun ${operation.name}(")
                append(generateServiceOperationParameterList(operation.parameters))
                appendLine("    )")
            }
        }
    }

    private fun generateServiceOperationParameterList(parameters: List<ServiceType.Operation.Parameter>): String =
        buildString {
            parameters.forEach { parameter ->
                val type = parameter.type as ResolvedTypeReference
                val fullyQualifiedName = generateFullyQualifiedNameForTypeReference(type)
                appendLine("        ${parameter.name}: ${fullyQualifiedName},")
            }
        }

    data class ProviderInfo(val implements: ProviderType.Implements) {
        val reference = implements.service as ResolvedTypeReference
        val service = reference.type as ServiceType
        val serviceArgumentName = service.name.replaceFirstChar { it.lowercase() }
    }

    private fun generateProvider(provider: ProviderType): String = buildString {
        check(provider.transport.name == "HTTP") { "Only HTTP transport is supported, this needs to be refactored later" }

        appendLine("import io.ktor.http.*")
        appendLine("import io.ktor.serialization.kotlinx.json.*")
        appendLine("import io.ktor.server.plugins.contentnegotiation.*")
        appendLine("import io.ktor.server.response.*")
        appendLine("import io.ktor.server.application.*")
        appendLine("import io.ktor.server.request.*")
        appendLine("import io.ktor.server.routing.*")
        appendLine("import kotlinx.serialization.json.*")
        appendLine()

        val implementedServices = provider.implements.map { ProviderInfo(it) }
        val serviceArguments = implementedServices.joinToString { info ->
            "${info.serviceArgumentName}: ${generateFullyQualifiedNameForTypeReference(info.reference)}"
        }
        appendLine("fun Routing.route${provider.name}($serviceArguments) {")
        implementedServices.forEach { info ->
            appendProviderOperations(info)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendProviderOperations(info: ProviderInfo) {
        info.implements.operations.forEach { operation ->
            when (operation) {
                is ServiceType.RequestResponseOperation -> {
                    // TODO Config: HTTP method?
                    // TODO Config: URL?
                    appendLine("    post(\"/${operation.name}\") {")
                    appendLine("        val bodyAsText = call.receiveText()")
                    appendLine("        val body = Json.parseToJsonElement(bodyAsText)\n")
                    appendLine()

                    operation.parameters.forEach { parameter ->
                        // TODO Config: From Body / from Query / from Path etc.
                        // TODO error and null handling
                        // TODO complexer data types than string

                        val typeRef = parameter.type.extractFullType()
                        if (typeRef.isOptional) {
                            appendLine("        val ${parameter.name} = body.jsonObject[\"${parameter.name}\"]?.jsonPrimitive?.contentOrNull")
                        } else {
                            appendLine("        val ${parameter.name} = body.jsonObject.getValue(\"${parameter.name}\").jsonPrimitive.content")
                        }
                    }
                    appendLine()

                    operation.parameters.forEach { parameter ->
                        // TODO constraints within map or list
                        val constraints = parameter.type.extractConstraints()
                        constraints.forEach { constraint ->
                            appendLine("        // TODO validate ${parameter.name} against ${constraint.humanReadableName}")
                        }
                    }
                    appendLine()

                    appendLine("        val response = ${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { it.name }})")

                    // TODO Config: HTTP status code

                    // TODO serialize response correctly
                    // TODO validate response
                    appendLine("        call.respond(response)")
                    appendLine("    }")
                }

                is ServiceType.OnewayOperation -> {
                    // TODO Config: HTTP method?
                    // TODO Config: URL?
                    appendLine("    post(\"/${operation.name}\") {")
                    appendLine("        val bodyAsText = call.receiveText()")
                    appendLine("        val body = Json.parseToJsonElement(bodyAsText)\n")
                    appendLine()

                    operation.parameters.forEach { parameter ->
                        // TODO Config: From Body / from Query / from Path etc.
                        // TODO error and null handling
                        // TODO complexer data types than string

                        val typeRef = parameter.type.extractFullType()
                        if (typeRef.isOptional) {
                            appendLine("        val ${parameter.name} = body.jsonObject[\"${parameter.name}\"]?.jsonPrimitive?.contentOrNull")
                        } else {
                            appendLine("        val ${parameter.name} = body.jsonObject.getValue(\"${parameter.name}\").jsonPrimitive.content")
                        }
                    }
                    appendLine()

                    operation.parameters.forEach { parameter ->
                        // TODO constraints within map or list
                        val constraints = parameter.type.extractConstraints()
                        constraints.forEach { constraint ->
                            appendLine("        // TODO validate ${parameter.name} against ${constraint.humanReadableName}")
                        }
                    }
                    appendLine()

                    // TODO call asynchronously
                    appendLine("        ${info.serviceArgumentName}.${operation.name}(${operation.parameters.joinToString { it.name }})")

                    appendLine("        call.respond(HttpStatusCode.NoContent)")
                    appendLine("    }")
                }
            }
        }
    }

    private fun TypeReference.extractConstraints(): List<ResolvedTypeReference.Constraint> {
        check(this is ResolvedTypeReference) { "Expected type reference to be resolved" }
        // TODO verify workst with aliases
        return constraints
    }

    private fun TypeReference.extractFullType(): ResolvedTypeReference {
        check(this is ResolvedTypeReference) { "Expected type reference to be resolved" }
        return if (type is AliasType) (type as AliasType).fullyResolvedType!! else this
    }

    private fun generateFullyQualifiedNameForTypeReference(reference: TypeReference): String {
        require(reference is ResolvedTypeReference) { "Expected type reference to be resolved" }
        val type = reference.type

        return buildString {
            val qualifiedName = when (type) {
                is PackageType -> type.sourcePackage.nameComponents.joinToString(".")
                is LiteralType -> mapSamtLiteralTypeToNativeType(type)
                is ListType -> mapSamtListTypeToNativeType(type.elementType)
                is MapType -> mapSamtMapTypeToNativeType(type.keyType, type.valueType)

                is UnknownType -> throw IllegalStateException("Expected type to be known")

                is UserDeclared -> {
                    val parentPackage = type.parentPackage
                    val components = parentPackage.nameComponents + type.name
                    components.joinToString(".")
                }
            }
            append(qualifiedName)

            if (reference.isOptional) {
                append("?")
            }
        }
    }

    private fun mapSamtLiteralTypeToNativeType(type: LiteralType): String = when (type) {
        StringType -> "String"
        BytesType -> "ByteArray"

        IntType -> "Int"
        LongType -> "Long"

        FloatType -> "Float"
        DoubleType -> "Double"

        DecimalType -> "java.math.BigDecimal"
        BooleanType -> "Boolean"

        DateType -> "java.time.LocalDate"
        DateTimeType -> "java.time.LocalDateTime"

        DurationType -> "java.time.Duration"
    }

    private fun mapSamtListTypeToNativeType(elementType: TypeReference): String {
        val element = generateFullyQualifiedNameForTypeReference(elementType)
        return "List<${element}>"
    }

    private fun mapSamtMapTypeToNativeType(keyType: TypeReference, valueType: TypeReference): String {
        val key = generateFullyQualifiedNameForTypeReference(keyType)
        val value = generateFullyQualifiedNameForTypeReference(valueType)
        return "Map<${key}, ${value}>"
    }

    private fun Package.hasModelTypes(): Boolean {
        return records.isNotEmpty() || enums.isNotEmpty() || services.isNotEmpty() || aliases.isNotEmpty()
    }

    private fun Package.hasProviderTypes(): Boolean {
        return providers.isNotEmpty()
    }

    companion object {
        fun generate(model: Package, controller: DiagnosticController): List<CodegenFile> = buildList {
            val generator = Codegen(model, controller)
            return generator.generate()
        }
    }
}
