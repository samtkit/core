package tools.samt.ls

import org.eclipse.lsp4j.SemanticTokensLegend
import tools.samt.common.Location
import tools.samt.parser.*
import tools.samt.semantic.*

class SamtSemanticTokens private constructor(userMetadata: UserMetadata) : SamtSemanticLookup<Location, SamtSemanticTokens.Metadata>(userMetadata) {
    override fun markType(node: ExpressionNode, type: Type) {
        super.markType(node, type)
        val location: Location
        if (node is BundleIdentifierNode) {
            location = node.components.last().location
            node.components.dropLast(1).forEach {
                this[it.location] = Metadata(TokenType.namespace)
            }
        } else {
            location = node.location
        }
        val modifier = (type as? UserDeclared)?.getDeprecationModifier() ?: TokenModifier.none
        when (type) {
            is ConsumerType -> this[location] = Metadata(TokenType.type, modifier)

            is EnumType -> this[location] = Metadata(TokenType.enum, modifier)

            is ListType -> {
                this[type.node.base.location] =
                    Metadata(TokenType.type, modifier and TokenModifier.defaultLibrary)
            }

            is MapType -> {
                this[type.node.base.location] =
                    Metadata(TokenType.type, modifier and TokenModifier.defaultLibrary)
            }

            is AliasType -> this[location] = Metadata(getAliasTokenType(type), modifier)
            is ProviderType -> this[location] = Metadata(TokenType.type, modifier)
            is RecordType -> this[location] = Metadata(TokenType.`class`, modifier)
            is ServiceType -> this[location] = Metadata(TokenType.`interface`, modifier)
            is LiteralType -> this[location] =
                Metadata(TokenType.type, modifier and TokenModifier.defaultLibrary)

            is PackageType -> this[location] = Metadata(TokenType.namespace, modifier)
            UnknownType -> this[location] = Metadata(TokenType.type, modifier)
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
        this[serviceType.declaration.name.location] = Metadata(TokenType.`interface`, serviceType.getDeprecationModifier() and TokenModifier.declaration)
    }

    override fun markOperationDeclaration(operation: ServiceType.Operation) {
        super.markOperationDeclaration(operation)
        var modifier = TokenModifier.declaration and operation.getDeprecationModifier()
        if (operation is ServiceType.RequestResponseOperation && operation.isAsync) {
            modifier = modifier and TokenModifier.async
        }
        this[operation.declaration.name.location] = Metadata(
            type = TokenType.method,
            modifier = modifier
        )
    }

    override fun markOperationParameterDeclaration(parameter: ServiceType.Operation.Parameter) {
        super.markOperationParameterDeclaration(parameter)
        this[parameter.declaration.name.location] = Metadata(TokenType.parameter, parameter.getDeprecationModifier() and TokenModifier.declaration)
    }

    override fun markRecordDeclaration(recordType: RecordType) {
        super.markRecordDeclaration(recordType)
        this[recordType.declaration.name.location] = Metadata(TokenType.`class`, recordType.getDeprecationModifier() and TokenModifier.declaration)
    }

    override fun markRecordFieldDeclaration(field: RecordType.Field) {
        super.markRecordFieldDeclaration(field)
        this[field.declaration.name.location] = Metadata(TokenType.property, field.getDeprecationModifier() and TokenModifier.declaration)
    }

    override fun markEnumDeclaration(enumType: EnumType) {
        super.markEnumDeclaration(enumType)
        this[enumType.declaration.name.location] = Metadata(TokenType.enum, enumType.getDeprecationModifier() and TokenModifier.declaration)
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
        var modifier = operation.getDeprecationModifier()
        if (operation is ServiceType.RequestResponseOperation && operation.isAsync) {
            modifier = modifier and TokenModifier.async
        }
        this[reference.location] = Metadata(
            type = TokenType.method,
            modifier = modifier
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

    override fun markTypeAliasDeclaration(aliasType: AliasType) {
        super.markTypeAliasDeclaration(aliasType)
        this[aliasType.declaration.name.location] = Metadata(getAliasTokenType(aliasType), aliasType.getDeprecationModifier() and TokenModifier.declaration)
    }

    private fun getAliasTokenType(aliasType: AliasType): TokenType = when (aliasType.fullyResolvedType?.type) {
        is EnumType -> TokenType.enum
        is RecordType -> TokenType.`class`
        is ServiceType -> TokenType.`interface`
        else -> TokenType.type
    }

    private fun UserDeclared.getDeprecationModifier() =
        if (userMetadata.getDeprecation(this) != null) {
            TokenModifier.deprecated
        } else {
            TokenModifier.none
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
            val deprecated = TokenModifier(1 shl 3)
            val entries = arrayOf(::declaration, ::async, ::defaultLibrary, ::deprecated)
        }
    }

    companion object {
        val legend = SemanticTokensLegend(
            TokenType.entries.map { it.name },
            TokenModifier.entries.map { it.name },
        )

        fun analyze(fileNode: FileNode, filePackage: Package, userMetadata: UserMetadata) =
            SamtSemanticTokens(userMetadata).also { it.analyze(fileNode, filePackage) }
    }
}
