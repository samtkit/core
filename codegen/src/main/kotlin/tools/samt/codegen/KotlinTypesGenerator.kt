package tools.samt.codegen

class KotlinTypesGenerator : Generator {
    override val name: String = "kotlin-types"
    override fun generate(generatorParams: GeneratorParams): List<CodegenFile> {
        generatorParams.packages.forEach {
            generatePackage(it)
        }
        return emittedFiles
    }

    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generatePackage(pack: SamtPackage) {
        if (pack.hasModelTypes()) {
            val packageSource = buildString {
                appendLine("package ${pack.qualifiedName}")
                appendLine()

                pack.records.forEach {
                    appendRecord(it)
                }

                pack.enums.forEach {
                    appendEnum(it)
                }

                pack.aliases.forEach {
                    appendAlias(it)
                }

                pack.services.forEach {
                    appendService(it)
                }
            }

            val filePath = pack.qualifiedName.replace('.', '/') + ".kt"
            val file = CodegenFile(filePath, packageSource)
            emittedFiles.add(file)
        }
    }

    private fun StringBuilder.appendRecord(record: RecordType) {
        appendLine("class ${record.name}(")
        record.fields.forEach { field ->
            val fullyQualifiedName = field.type.qualifiedName
            val isOptional = field.type.isOptional

            if (isOptional) {
                appendLine("    val ${field.name}: $fullyQualifiedName = null,")
            } else {
                appendLine("    val ${field.name}: $fullyQualifiedName,")
            }
        }
        appendLine(")")
        appendLine()
    }

    private fun StringBuilder.appendEnum(enum: EnumType) {
        appendLine("enum class ${enum.name} {")
        enum.values.forEach {
            appendLine("    ${it},")
        }
        appendLine("}")
    }

    private fun StringBuilder.appendAlias(alias: AliasType) {
        appendLine("typealias ${alias.name} = ${alias.aliasedType.qualifiedName}")
    }

    private fun StringBuilder.appendService(service: ServiceType) {
        appendLine("interface ${service.name} {")
        service.operations.forEach { operation ->
            appendServiceOperation(operation)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendServiceOperation(operation: ServiceOperation) {
        when (operation) {
            is RequestResponseOperation -> {
                // method head
                if (operation.isAsync) {
                    appendLine("    suspend fun ${operation.name}(")
                } else {
                    appendLine("    fun ${operation.name}(")
                }

                // parameters
                appendServiceOperationParameterList(operation.parameters)

                // return type
                if (operation.returnType != null) {
                    appendLine("    ): ${operation.returnType!!.qualifiedName}")
                } else {
                    appendLine("    )")
                }
            }

            is OnewayOperation -> {
                appendLine("    fun ${operation.name}(")
                appendServiceOperationParameterList(operation.parameters)
                appendLine("    )")
            }
        }
    }

    private fun StringBuilder.appendServiceOperationParameterList(parameters: List<ServiceOperationParameter>) {
        parameters.forEach { parameter ->
            appendLine("        ${parameter.name}: ${parameter.type.qualifiedName},")
        }
    }

    private fun SamtPackage.hasModelTypes(): Boolean {
        return records.isNotEmpty() || enums.isNotEmpty() || services.isNotEmpty() || aliases.isNotEmpty()
    }
}
