package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.parser.StringNode

internal class SemanticModelAnnotationProcessor(
        private val controller: DiagnosticController
) {
    fun process(global: Package): UserMetadata {
        val descriptions = mutableMapOf<UserDeclared, String>()
        val deprecatedTypes = mutableSetOf<UserDeclared>()
        for (element in global.getAnnotatedElements()) {
            for (annotation in element.annotations) {
                val context = controller.getOrCreateContext(annotation.location.source)
                when (val name = annotation.name.name) {
                    "Description" -> {
                        val description = annotation.arguments.firstOrNull() as? StringNode
                        if (description == null || annotation.arguments.size != 1) {
                            context.error {
                                message("Description annotation must have exactly one string as an argument")
                                highlight("invalid annotation", annotation.location)
                            }
                        }
                        description?.also { descriptions[element] = it.value }
                    }
                    "Deprecated" -> {
                        if (annotation.arguments.isNotEmpty()) {
                            context.error {
                                message("Deprecated annotation must have no arguments")
                                highlight("invalid annotation", annotation.location)
                            }
                        }
                        deprecatedTypes.add(element)
                    }
                    else -> {
                        context.error {
                            message("Unknown annotation '${name}'")
                            highlight("invalid annotation", annotation.location)
                        }
                    }
                }
            }
        }
        return UserMetadata(descriptions, deprecatedTypes)
    }

    private fun Package.getAnnotatedElements(): List<UserDeclared> = buildList {
        this@getAnnotatedElements.allSubPackages.forEach {
            addAll(it.records)
            it.records.flatMapTo(this, RecordType::fields)
            addAll(it.enums)
            addAll(it.aliases)
            addAll(it.services)
            val operations = it.services.flatMap(ServiceType::operations)
            addAll(operations)
            operations.flatMapTo(this, ServiceType.Operation::parameters)
        }
    }

    private val UserDeclared.annotations
        get() = when (this) {
            is RecordType -> declaration.annotations
            is RecordType.Field -> declaration.annotations
            is EnumType -> declaration.annotations
            is ServiceType -> declaration.annotations
            is ServiceType.Operation -> declaration.annotations
            is ServiceType.Operation.Parameter -> declaration.annotations
            is AliasType -> declaration.annotations
            is ConsumerType, is ProviderType -> emptyList()
        }
}
