package tools.samt.ls

import tools.samt.common.Location
import tools.samt.parser.BundleIdentifierNode
import tools.samt.parser.ExpressionNode
import tools.samt.parser.FileNode
import tools.samt.parser.IdentifierNode
import tools.samt.semantic.Package
import tools.samt.semantic.ServiceType
import tools.samt.semantic.Type
import tools.samt.semantic.UserDeclared

class SamtReferencesLookup private constructor() : SamtSemanticLookup<UserDeclared, List<Location>>() {
    private fun addUsage(declaration: UserDeclared, usage: Location) {
        if (this[declaration] == null) {
            this[declaration] = mutableListOf()
        }
        (this[declaration] as MutableList<Location>) += usage
    }

    override fun markType(node: ExpressionNode, type: Type) {
        super.markType(node, type)

        if (type is UserDeclared) {
            if (node is BundleIdentifierNode) {
                addUsage(type, node.components.last().location)
            } else {
                addUsage(type, node.location)
            }
        }
    }

    override fun markOperationReference(operation: ServiceType.Operation, reference: IdentifierNode) {
        super.markOperationReference(operation, reference)
        addUsage(operation, reference.location)
    }

    companion object {
        fun analyze(filesAndPackages: List<Pair<FileNode, Package>>): SamtReferencesLookup {
            val lookup = SamtReferencesLookup()
            for ((fileInfo, samtPackage) in filesAndPackages) {
                lookup.analyze(fileInfo, samtPackage)
            }
            return lookup
        }
    }
}
