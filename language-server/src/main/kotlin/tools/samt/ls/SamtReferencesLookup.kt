package tools.samt.ls

import tools.samt.common.Location
import tools.samt.parser.BundleIdentifierNode
import tools.samt.parser.ExpressionNode
import tools.samt.parser.FileNode
import tools.samt.parser.IdentifierNode
import tools.samt.semantic.*

class SamtReferencesLookup private constructor(userMetadata: UserMetadata) : SamtSemanticLookup<UserDeclared, List<Location>>(userMetadata) {
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
        fun analyze(filesAndPackages: List<Pair<FileNode, Package>>, userMetadata: UserMetadata): SamtReferencesLookup {
            val lookup = SamtReferencesLookup(userMetadata)
            for ((fileInfo, filePackage) in filesAndPackages) {
                lookup.analyze(fileInfo, filePackage)
            }
            return lookup
        }
    }
}
