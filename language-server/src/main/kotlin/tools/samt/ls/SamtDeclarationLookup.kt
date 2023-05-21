package tools.samt.ls

import tools.samt.common.Location
import tools.samt.parser.BundleIdentifierNode
import tools.samt.parser.ExpressionNode
import tools.samt.parser.FileNode
import tools.samt.parser.IdentifierNode
import tools.samt.semantic.*

class SamtDeclarationLookup private constructor() : SamtSemanticLookup<Location, UserDeclared>() {
    override fun markType(node: ExpressionNode, type: Type) {
        super.markType(node, type)

        if (type is UserDeclared) {
            if (node is BundleIdentifierNode) {
                this[node.components.last().location] = type
            } else {
                this[node.location] = type
            }
        }
    }

    override fun markOperationReference(operation: ServiceType.Operation, reference: IdentifierNode) {
        super.markOperationReference(operation, reference)
        this[reference.location] = operation
    }

    override fun markProviderDeclaration(providerType: ProviderType) {
        super.markProviderDeclaration(providerType)
        this[providerType.declaration.name.location] = providerType
    }

    override fun markServiceDeclaration(serviceType: ServiceType) {
        super.markServiceDeclaration(serviceType)
        this[serviceType.declaration.name.location] = serviceType
    }

    override fun markRecordDeclaration(recordType: RecordType) {
        super.markRecordDeclaration(recordType)
        this[recordType.declaration.name.location] = recordType
    }

    override fun markOperationDeclaration(operation: ServiceType.Operation) {
        super.markOperationDeclaration(operation)
        this[operation.declaration.name.location] = operation
    }

    override fun markTypeAliasDeclaration(aliasType: AliasType) {
        super.markTypeAliasDeclaration(aliasType)
        this[aliasType.declaration.name.location] = aliasType
    }

    override fun markEnumDeclaration(enumType: EnumType) {
        super.markEnumDeclaration(enumType)
        this[enumType.declaration.name.location] = enumType
    }

    companion object {
        fun analyze(fileNode: FileNode, samtPackage: Package, userMetadata: UserMetadata) =
            SamtDeclarationLookup().also { it.analyze(fileNode, samtPackage, userMetadata) }
    }
}
