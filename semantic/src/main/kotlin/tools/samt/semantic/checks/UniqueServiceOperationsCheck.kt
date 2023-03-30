package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.FileNode
import tools.samt.parser.ServiceDeclarationNode
import tools.samt.semantic.SemanticCheck

internal class UniqueServiceOperationsCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.filterIsInstance<ServiceDeclarationNode>().forEach { service ->
            val existingOperationNames = mutableMapOf<String, Location>()
            service.operations.forEach { operation ->
                val operationName = operation.name
                val existingLocation = existingOperationNames.putIfAbsent(operationName.name, operationName.location)
                if (existingLocation != null) {
                    diagnostics.error {
                        message("Operation '${operationName.name}' is already defined")
                        highlight("duplicate definition", operationName.location)
                        highlight("previous definition", existingLocation)
                    }
                }
            }
        }
    }
}
