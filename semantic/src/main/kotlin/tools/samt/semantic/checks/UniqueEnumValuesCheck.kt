package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.EnumDeclarationNode
import tools.samt.parser.FileNode
import tools.samt.semantic.SemanticCheck

internal class UniqueEnumValuesCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.filterIsInstance<EnumDeclarationNode>().forEach { enum ->
            val existingValues = mutableMapOf<String, Location>()
            enum.values.forEach { value ->
                val existingLocation = existingValues.putIfAbsent(value.name, value.location)
                if (existingLocation != null) {
                    diagnostics.error {
                        message("Enum value '${value.name}' is defined more than once")
                        highlight("duplicate definition", value.location)
                        highlight("previous definition", existingLocation)
                    }
                }
            }
        }
    }
}
