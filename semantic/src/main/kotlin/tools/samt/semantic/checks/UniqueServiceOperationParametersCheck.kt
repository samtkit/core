package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.FileNode
import tools.samt.parser.ServiceDeclarationNode
import tools.samt.semantic.SemanticCheck

internal class UniqueServiceOperationParametersCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.filterIsInstance<ServiceDeclarationNode>().forEach { service ->
            service.operations.forEach { operation ->
                val existingParameterNames = mutableMapOf<String, Location>()
                operation.parameters.forEach { parameter ->
                    val parameterName = parameter.name
                    val existingLocation = existingParameterNames.putIfAbsent(parameterName.name, parameterName.location)
                    if (existingLocation != null) {
                        diagnostics.error {
                            message("Parameter `${parameterName.name}` for operation '${operation.name.name}' is already defined")
                            highlight("duplicate definition", parameterName.location)
                            highlight("previous definition", existingLocation)
                        }
                    }
                }
            }
        }
    }
}
