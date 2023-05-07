package tools.samt.ls

import org.eclipse.lsp4j.SemanticTokensLegend
import tools.samt.common.Location
import tools.samt.parser.*
import tools.samt.semantic.*

class SamtSemanticTokens private constructor() : SamtSemanticLookup<Location, SamtSemanticTokens.Metadata>() {
    override fun markType(node: ExpressionNode, type: Type) {
        super.markType(node, type)
        val location = if (node is BundleIdentifierNode) {
            node.components.last().location
        } else {
            node.location
        }
        when (type) {
            is ConsumerType -> this[location] = Metadata(TokenType.type)

            is EnumType -> this[location] = Metadata(TokenType.enum)

            is ListType -> {
                this[type.node.base.location] =
                    Metadata(TokenType.type, TokenModifier.defaultLibrary)
            }

            is MapType -> {
                this[type.node.base.location] =
                    Metadata(TokenType.type, TokenModifier.defaultLibrary)
            }

            is ProviderType -> this[location] = Metadata(TokenType.type)
            is RecordType -> this[location] = Metadata(TokenType.`class`)
            is ServiceType -> this[location] = Metadata(TokenType.`interface`)
            is LiteralType -> this[location] =
                Metadata(TokenType.type, TokenModifier.defaultLibrary)

            is PackageType -> this[location] = Metadata(TokenType.namespace)
            UnknownType -> this[location] = Metadata(TokenType.type)
        }
    }

    override fun markConstraints(constraints: List<ResolvedTypeReference.Constraint>) {
        super.markConstraints(constraints)
        for (constraint in constraints.map { it.node }.filterIsInstance<CallExpressionNode>()) {
            this[constraint.base.location] = Metadata(TokenType.function, TokenModifier.defaultLibrary)
        }
    }

    override fun markAnnotations(annotations: List<AnnotationNode>) {
        super.markAnnotations(annotations)
        for (annotation in annotations) {
            this[annotation.name.location] = Metadata(TokenType.type, TokenModifier.defaultLibrary)
        }
    }

    override fun markServiceDeclaration(serviceType: ServiceType) {
        super.markServiceDeclaration(serviceType)
        this[serviceType.declaration.name.location] = Metadata(TokenType.`interface`, TokenModifier.declaration)
    }

    override fun markOperationDeclaration(operation: ServiceType.Operation) {
        super.markOperationDeclaration(operation)
        this[operation.declaration.name.location] = Metadata(
            type = TokenType.method,
            modifier = if (operation is ServiceType.RequestResponseOperation && operation.isAsync) {
                TokenModifier.declaration and TokenModifier.async
            } else {
                TokenModifier.declaration
            }
        )
    }

    override fun markOperationParameterDeclaration(parameter: ServiceType.Operation.Parameter) {
        super.markOperationParameterDeclaration(parameter)
        this[parameter.declaration.name.location] = Metadata(TokenType.parameter, TokenModifier.declaration)
    }

    override fun markRecordDeclaration(recordType: RecordType) {
        super.markRecordDeclaration(recordType)
        this[recordType.declaration.name.location] = Metadata(TokenType.`class`, TokenModifier.declaration)
    }

    override fun markRecordFieldDeclaration(field: RecordType.Field) {
        super.markRecordFieldDeclaration(field)
        this[field.declaration.name.location] = Metadata(TokenType.property, TokenModifier.declaration)
    }

    override fun markEnumDeclaration(enumType: EnumType) {
        super.markEnumDeclaration(enumType)
        this[enumType.declaration.name.location] = Metadata(TokenType.enum, TokenModifier.declaration)
        for (enumMember in enumType.declaration.values) {
            this[enumMember.location] = Metadata(TokenType.enumMember, TokenModifier.declaration)
        }
    }

    override fun markProviderDeclaration(providerType: ProviderType) {
        super.markProviderDeclaration(providerType)
        this[providerType.declaration.name.location] = Metadata(TokenType.type, TokenModifier.declaration)
    }

    override fun markOperationReference(operation: ServiceType.Operation, reference: IdentifierNode) {
        super.markOperationReference(operation, reference)
        this[reference.location] = Metadata(
            type = TokenType.method,
            modifier = if (operation is ServiceType.RequestResponseOperation && operation.isAsync) {
                TokenModifier.async
            } else {
                TokenModifier.none
            }
        )
    }

    override fun markPackageDeclaration(packageDeclaration: PackageDeclarationNode) {
        super.markPackageDeclaration(packageDeclaration)
        for (component in packageDeclaration.name.components) {
            this[component.location] = Metadata(TokenType.namespace)
        }
    }

    override fun markImport(import: ImportNode, importedType: Type) {
        super.markImport(import, importedType)
        val typeLocation = import.name.components.last().location
        if (import is TypeImportNode && import.alias != null) {
            this[import.alias!!.location] = this[typeLocation]!!.copy(modifier = TokenModifier.declaration)
        }
    }

    data class Metadata(val type: TokenType, val modifier: TokenModifier = TokenModifier.none)

    @Suppress("EnumEntryName")
    enum class TokenType {
        /** SAMT Operations */
        method,

        /** SAMT Constraints */
        function,

        /** SAMT Enum Member */
        enumMember,

        /** SAMT Record Field */
        property,

        /** SAMT Operation Parameter */
        parameter,

        /** SAMT Record */
        `class`,

        /** SAMT Enum */
        `enum`,

        /** SAMT Service */
        `interface`,

        /** SAMT Consumer & Provider */
        type,

        /** SAMT Package */
        namespace,
    }

    @JvmInline
    value class TokenModifier private constructor(val bitmask: Int) {

        infix fun and(other: TokenModifier) = TokenModifier(this.bitmask.or(other.bitmask))

        companion object {
            val none = TokenModifier(0)
            val declaration = TokenModifier(1 shl 0)
            val async = TokenModifier(1 shl 1)
            val defaultLibrary = TokenModifier(1 shl 2)
            fun values() = arrayOf(::declaration, ::async, ::defaultLibrary)
        }
    }

    companion object {
        val legend = SemanticTokensLegend(
            TokenType.values().map { it.name },
            TokenModifier.values().map { it.name },
        )

        fun analyze(fileNode: FileNode, samtPackage: Package) =
            SamtSemanticTokens().also { it.analyze(fileNode, samtPackage) }
    }
}
