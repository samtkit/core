package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.common.Location
import tools.samt.parser.FileNode
import tools.samt.parser.RecordDeclarationNode
import tools.samt.semantic.SemanticCheck

internal class UniqueRecordFieldsCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        // This code will have to be moved to the type system once inheritance is implemented
        fileNode.statements.filterIsInstance<RecordDeclarationNode>().forEach { record ->
            val existingFields = mutableMapOf<String, Location>()
            record.fields.forEach { field ->
                val fieldName = field.name
                val existingLocation = existingFields.putIfAbsent(fieldName.name, fieldName.location)
                if (existingLocation != null) {
                    diagnostics.error {
                        message("Record field '${fieldName.name}' is already defined")
                        highlight("duplicate definition", fieldName.location)
                        highlight("previous definition", existingLocation)
                    }
                }
            }
        }
    }
}
