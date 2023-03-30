package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.ConsumerDeclarationNode
import tools.samt.parser.FileNode
import tools.samt.semantic.SemanticCheck

internal class UniqueConsumerUsesOperationsCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.filterIsInstance<ConsumerDeclarationNode>().forEach { consumer ->
            consumer.usages.forEach { uses ->
                val existingOperationNames = mutableMapOf<String, Location>()
                uses.serviceOperationNames.forEach { operationName ->
                    val existingLocation =
                        existingOperationNames.putIfAbsent(operationName.name, operationName.location)
                    if (existingLocation != null) {
                        diagnostics.error {
                            message("Operation '${operationName.name}' is already used")
                            highlight("duplicate operation reference", operationName.location)
                            highlight("previous operation reference", existingLocation)
                        }
                    }
                }
            }
        }
    }
}
