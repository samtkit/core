package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.parser.AnnotationNode
import tools.samt.parser.StringNode

internal class SemanticModelAnnotationProcessor(
        private val controller: DiagnosticController
) {
    fun process(global: Package): UserMetadata {
        val descriptions = mutableMapOf<UserDeclared, String>()
        val deprecations = mutableMapOf<UserDeclared, UserMetadata.Deprecation>()
        for (element in global.getAnnotatedElements()) {
            for (annotation in element.annotations) {
                val context = controller.getOrCreateContext(annotation.location.source)
                when (val name = annotation.name.name) {
                    "Description" -> descriptions[element] = getDescription(annotation)
                    "Deprecated" -> deprecations[element] = getDeprecation(annotation)
                    else -> {
                        context.error {
                            message("Unknown annotation '${name}'")
                            highlight("invalid annotation", annotation.location)
                        }
                    }
                }
            }
        }
        return UserMetadata(descriptions, deprecations)
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

    private fun getDescription(annotation: AnnotationNode): String {
        check(annotation.name.name == "Description")
        val arguments = annotation.arguments
        val context = controller.getOrCreateContext(annotation.location.source)
        if (arguments.isEmpty()) {
            context.error {
                message("Missing argument for @Description annotation")
                highlight("invalid annotation", annotation.location)
            }
            return ""
        }
        if (arguments.size > 1) {
            val errorLocation = arguments[1].location.copy(end = arguments.last().location.end)
            context.error {
                message("Description annotation must have only one argument")
                highlight("extraneous arguments", errorLocation)
            }
        }
        return when (val description = arguments.first()) {
            is StringNode -> description.value
            else -> {
                context.error {
                    message("Argument for @Description must be a string")
                    highlight("invalid argument type", description.location)
                }
                ""
            }
        }
    }

    private fun getDeprecation(annotation: AnnotationNode): UserMetadata.Deprecation {
        check(annotation.name.name == "Deprecated")
        val context = controller.getOrCreateContext(annotation.location.source)
        val description = annotation.arguments.firstOrNull()
        if (description != null && description !is StringNode) {
            context.error {
                message("Argument for @Deprecated must be a string")
                highlight("invalid argument type", description.location)
            }
        }
        if (annotation.arguments.size > 1) {
            val errorLocation = annotation.arguments[1].location.copy(end = annotation.arguments.last().location.end)
            context.error {
                message("Deprecated annotation can have at most one argument")
                highlight("extraneous arguments", errorLocation)
            }
        }
        return UserMetadata.Deprecation((description as? StringNode)?.value)
    }
}
