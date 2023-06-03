package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.parser.AnnotationNode
import tools.samt.parser.StringNode
import tools.samt.parser.reportError

internal class SemanticModelAnnotationProcessor(
    private val controller: DiagnosticController,
) {
    fun process(global: Package): UserMetadata {
        val descriptions = mutableMapOf<UserDeclared, String>()
        val deprecations = mutableMapOf<UserDeclared, UserMetadata.Deprecation>()
        for (element in global.getAnnotatedElements()) {
            for (annotation in element.annotations) {
                when (val name = annotation.name.name) {
                    "Description" -> {
                        if (element in descriptions) {
                            annotation.reportError(controller) {
                                message("Duplicate @Description annotation")
                                highlight("duplicate annotation", annotation.location)
                                highlight(
                                    "previous annotation",
                                    element.annotations.first { it.name.name == "Description" }.location
                                )
                            }
                        }
                        descriptions[element] = getDescription(annotation)
                    }

                    "Deprecated" -> {
                        if (element in deprecations) {
                            annotation.reportError(controller) {
                                message("Duplicate @Deprecated annotation")
                                highlight("duplicate annotation", annotation.location)
                                highlight(
                                    "previous annotation",
                                    element.annotations.first { it.name.name == "Deprecated" }.location
                                )
                            }
                        }
                        deprecations[element] = getDeprecation(annotation)
                    }

                    else -> {
                        annotation.reportError(controller) {
                            message("Unknown annotation @${name}, allowed annotations are @Description and @Deprecated")
                            highlight("invalid annotation", annotation.location)
                        }
                    }
                }
            }
        }
        return UserMetadata(descriptions, deprecations)
    }

    private fun Package.getAnnotatedElements(): List<UserAnnotated> = buildList {
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

    private fun getDescription(annotation: AnnotationNode): String {
        check(annotation.name.name == "Description")
        val arguments = annotation.arguments
        if (arguments.isEmpty()) {
            annotation.reportError(controller) {
                message("Missing argument for @Description")
                highlight("invalid annotation", annotation.location)
            }
            return ""
        }
        if (arguments.size > 1) {
            val errorLocation = arguments[1].location.copy(end = arguments.last().location.end)
            annotation.reportError(controller) {
                message("@Description expects exactly one string argument")
                highlight("extraneous arguments", errorLocation)
            }
        }
        return when (val description = arguments.first()) {
            is StringNode -> description.value
            else -> {
                annotation.reportError(controller) {
                    message("Argument for @Description must be a string")
                    highlight("invalid argument type", description.location)
                }
                ""
            }
        }
    }

    private fun getDeprecation(annotation: AnnotationNode): UserMetadata.Deprecation {
        check(annotation.name.name == "Deprecated")
        val description = annotation.arguments.firstOrNull()
        if (description != null && description !is StringNode) {
            annotation.reportError(controller) {
                message("Argument for @Deprecated must be a string")
                highlight("invalid argument type", description.location)
            }
        }
        if (annotation.arguments.size > 1) {
            val errorLocation = annotation.arguments[1].location.copy(end = annotation.arguments.last().location.end)
            annotation.reportError(controller) {
                message("@Deprecated expects at most one string argument")
                highlight("extraneous arguments", errorLocation)
            }
        }
        return UserMetadata.Deprecation((description as? StringNode)?.value)
    }
}
