package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.FileNode
import tools.samt.parser.ProviderDeclarationNode
import tools.samt.semantic.SemanticCheck

internal class UniqueProviderImplementsOperationsCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.filterIsInstance<ProviderDeclarationNode>().forEach { provider ->
            provider.implements.forEach { implements ->
                val existingOperationNames = mutableMapOf<String, Location>()
                implements.serviceOperationNames.forEach { operationName ->
                    val existingLocation =
                        existingOperationNames.putIfAbsent(operationName.name, operationName.location)
                    if (existingLocation != null) {
                        diagnostics.error {
                            message("Operation '${operationName.name}' is already implemented")
                            highlight("duplicate operation reference", operationName.location)
                            highlight("previous operation reference", existingLocation)
                        }
                    }
                }
            }
        }
    }
}
