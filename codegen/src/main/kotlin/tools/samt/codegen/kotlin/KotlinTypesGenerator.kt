package tools.samt.codegen.kotlin

import tools.samt.codegen.*

object KotlinTypesGenerator : Generator {
    override val name: String = "kotlin-types"
    override fun generate(generatorParams: GeneratorParams): List<CodegenFile> {
        generatorParams.packages.forEach {
            generatePackage(it, generatorParams.options)
        }
        val result = emittedFiles.toList()
        emittedFiles.clear()
        return result
    }

    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generatePackage(pack: SamtPackage, options: Map<String, String>) {
        if (pack.hasModelTypes()) {
            val packageSource = buildString {
                appendLine(GeneratedFilePreamble)
                appendLine()
                appendLine("package ${pack.getQualifiedName(options)}")
                appendLine()

                pack.records.forEach {
                    appendRecord(it, options)
                }

                pack.enums.forEach {
                    appendEnum(it)
                }

                pack.aliases.forEach {
                    appendAlias(it, options)
                }

                pack.services.forEach {
                    appendService(it, options)
                }
            }

            val filePath = "${pack.getQualifiedName(options).replace('.', '/')}/Types.kt"
            val file = CodegenFile(filePath, packageSource)
            emittedFiles.add(file)
        }
    }

    private fun StringBuilder.appendRecord(record: RecordType, options: Map<String, String>) {
        appendLine("class ${record.name}(")
        record.fields.forEach { field ->
            val fullyQualifiedName = field.type.getQualifiedName(options)
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
        appendLine("    /** Default value used when the enum could not be parsed */")
        appendLine("    FAILED_TO_PARSE,")
        enum.values.forEach {
            appendLine("    ${it},")
        }
        appendLine("}")
    }

    private fun StringBuilder.appendAlias(alias: AliasType, options: Map<String, String>) {
        appendLine("typealias ${alias.name} = ${alias.aliasedType.getQualifiedName(options)}")
    }

    private fun StringBuilder.appendService(service: ServiceType, options: Map<String, String>) {
        appendLine("interface ${service.name} {")
        service.operations.forEach { operation ->
            appendServiceOperation(operation, options)
        }
        appendLine("}")
    }

    private fun StringBuilder.appendServiceOperation(operation: ServiceOperation, options: Map<String, String>) {
        when (operation) {
            is RequestResponseOperation -> {
                // method head
                if (operation.isAsync) {
                    appendLine("    suspend fun ${operation.name}(")
                } else {
                    appendLine("    fun ${operation.name}(")
                }

                // parameters
                appendServiceOperationParameterList(operation.parameters, options)

                // return type
                if (operation.returnType != null) {
                    appendLine("    ): ${operation.returnType!!.getQualifiedName(options)}")
                } else {
                    appendLine("    )")
                }
            }

            is OnewayOperation -> {
                appendLine("    fun ${operation.name}(")
                appendServiceOperationParameterList(operation.parameters, options)
                appendLine("    )")
            }
        }
    }

    private fun StringBuilder.appendServiceOperationParameterList(parameters: List<ServiceOperationParameter>, options: Map<String, String>) {
        parameters.forEach { parameter ->
            val fullyQualifiedName = parameter.type.getQualifiedName(options)
            val isOptional = parameter.type.isOptional

            if (isOptional) {
                appendLine("        ${parameter.name}: $fullyQualifiedName = null,")
            } else {
                appendLine("        ${parameter.name}: $fullyQualifiedName,")
            }
        }
    }

    private fun SamtPackage.hasModelTypes(): Boolean {
        return records.isNotEmpty() || enums.isNotEmpty() || services.isNotEmpty() || aliases.isNotEmpty()
    }
}
