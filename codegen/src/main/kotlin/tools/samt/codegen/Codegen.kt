package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.semantic.*

data class CodegenFile(val filepath: String, val source: String)

/*
 * Proof of concept codegen for Kotlin code
 *
 * Todos:
 * - Emit services
 * - Emit providers
 * - Emit consumers
 * - Emit aliases
 * - Modular
 * - Extendable
 * - Configurable
 * */
class Codegen(
    private val model: Package,
    private val controller: DiagnosticController
) {
    private val emittedFiles = mutableListOf<CodegenFile>()

    private fun generate(): List<CodegenFile> {
        generatePackage(model)
        return emittedFiles
    }

    private fun generatePackage(pack: Package) {
        pack.records.forEach { generateRecord(it) }
        pack.enums.forEach { generateEnum(it) }
        pack.subPackages.forEach { generatePackage(it) }
    }

    private fun generateRecord(record: RecordType) {
        val parentPackage = record.parentPackage
        val packagePath = parentPackage.nameComponents.joinToString("/")
        val filepath = "${packagePath}/${record.name}.kt"

        val source = buildString {
            appendLine("package ${parentPackage.nameComponents.joinToString(".")}")

            appendLine("class ${record.name} {")
            record.fields.forEach { field ->
                val fullyQualifiedName = generateFullyQualifiedNameForTypeReference(field.type)
                appendLine("    val ${field.name}: ${fullyQualifiedName}")
            }
            appendLine("}")
        }
        emittedFiles.add(CodegenFile(filepath, source))
    }

    private fun generateEnum(enum: EnumType) {
        val parentPackage = enum.parentPackage
        val packagePath = parentPackage.nameComponents.joinToString("/")
        val filepath = "${packagePath}/${enum.name}.kt"

        val source = buildString {
            appendLine("package ${parentPackage.nameComponents.joinToString(".")}")

            appendLine("enum ${enum.name} {")
            enum.values.forEach {
                appendLine("    ${it},")
            }
            appendLine("}")
        }
        emittedFiles.add(CodegenFile(filepath, source))
    }

    private fun generateFullyQualifiedNameForTypeReference(reference: TypeReference): String {
        require(reference is ResolvedTypeReference) { "Expected type reference to be resolved" }

        val reference: ResolvedTypeReference = reference
        val type = reference.type

        return buildString {
            val qualifiedName = when (type) {
                is PackageType -> type.sourcePackage.nameComponents.joinToString(".")
                is LiteralType -> type.humanReadableName
                is ListType -> "List<${generateFullyQualifiedNameForTypeReference(type.elementType)}>"
                is MapType -> "Map<${generateFullyQualifiedNameForTypeReference(type.keyType)}, ${generateFullyQualifiedNameForTypeReference(type.valueType)}>"

                is UserDeclared -> {
                    val parentPackage = type.parentPackage
                    val components = parentPackage.nameComponents + type.name
                    components.joinToString(".")
                }

                is UnknownType -> throw IllegalStateException("Expected type to be known")
            }
            append(qualifiedName)

            if (reference.isOptional) {
                append("?")
            }
        }
    }

    companion object {
        fun generate(model: Package, controller: DiagnosticController): List<CodegenFile> = buildList {
            val generator = Codegen(model, controller)
            return generator.generate()
        }
    }
}