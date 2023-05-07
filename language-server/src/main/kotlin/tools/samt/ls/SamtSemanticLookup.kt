package tools.samt.ls

import tools.samt.parser.*
import tools.samt.semantic.*

abstract class SamtSemanticLookup<TKey, TValue> protected constructor() {
    protected fun analyze(fileNode: FileNode, samtPackage: Package) {
        for (import in fileNode.imports) {
            markStatement(samtPackage, import)
        }
        markStatement(samtPackage, fileNode.packageDeclaration)
        for (statement in fileNode.statements) {
            markStatement(samtPackage, statement)
        }
    }

    operator fun get(key: TKey) = lookup[key]
    operator fun set(key: TKey, value: TValue) {
        lookup[key] = value
    }

    private val lookup = mutableMapOf<TKey, TValue>()

    protected open fun markType(node: ExpressionNode, type: Type) {
        when (type) {
            is ListType -> {
                markTypeReference(type.elementType)
            }

            is MapType -> {
                markTypeReference(type.keyType)
                markTypeReference(type.valueType)
            }

            is ConsumerType,
            is EnumType,
            is ProviderType,
            is RecordType,
            is ServiceType,
            is LiteralType,
            is PackageType,
            UnknownType,
            -> Unit
        }
    }

    protected open fun markTypeReference(reference: TypeReference) {
        check(reference is ResolvedTypeReference) { "Unresolved type reference shouldn't be here" }
        markType(reference.typeNode, reference.type)
        markConstraints(reference.constraints)
    }

    protected open fun markConstraints(constraints: List<ResolvedTypeReference.Constraint>) {}

    protected open fun markAnnotations(annotations: List<AnnotationNode>) {}

    protected open fun markStatement(samtPackage: Package, statement: StatementNode) {
        when (statement) {
            is ConsumerDeclarationNode -> markConsumerDeclaration(samtPackage.getTypeByNode(statement))
            is ProviderDeclarationNode -> markProviderDeclaration(samtPackage.getTypeByNode(statement))
            is EnumDeclarationNode -> markEnumDeclaration(samtPackage.getTypeByNode(statement))
            is RecordDeclarationNode -> markRecordDeclaration(samtPackage.getTypeByNode(statement))
            is ServiceDeclarationNode -> markServiceDeclaration(samtPackage.getTypeByNode(statement))
            is TypeAliasNode -> Unit
            is PackageDeclarationNode -> markPackageDeclaration(statement)
            is ImportNode -> markImport(statement,samtPackage.typeByNode[statement] ?: UnknownType)
        }
    }

    protected open fun markServiceDeclaration(serviceType: ServiceType) {
        markAnnotations(serviceType.declaration.annotations)
        for (operation in serviceType.operations) {
            markOperationDeclaration(operation)
        }
    }

    protected open fun markOperationDeclaration(operation: ServiceType.Operation) {
        markAnnotations(operation.declaration.annotations)
        for (parameter in operation.parameters) {
            markOperationParameterDeclaration(parameter)
        }
        when (operation) {
            is ServiceType.OnewayOperation -> Unit
            is ServiceType.RequestResponseOperation -> {
                operation.raisesTypes.forEach { markTypeReference(it) }
                operation.returnType?.let { markTypeReference(it) }
            }
        }
    }

    protected open fun markOperationParameterDeclaration(parameter: ServiceType.Operation.Parameter) {
        markAnnotations(parameter.declaration.annotations)
        markTypeReference(parameter.type)
    }

    protected open fun markRecordDeclaration(recordType: RecordType) {
        markAnnotations(recordType.declaration.annotations)
        for (field in recordType.fields) {
            markRecordFieldDeclaration(field)
        }
    }

    protected open fun markRecordFieldDeclaration(field: RecordType.Field) {
        markAnnotations(field.declaration.annotations)
        markTypeReference(field.type)
    }

    protected open fun markEnumDeclaration(enumType: EnumType) {
        markAnnotations(enumType.declaration.annotations)
    }

    protected open fun markProviderDeclaration(providerType: ProviderType) {
        for (implements in providerType.implements) {
            markTypeReference(implements.service)
            markOperationReference(implements.operations, implements.node.serviceOperationNames)
        }
    }

    protected open fun markConsumerDeclaration(consumerType: ConsumerType) {
        markTypeReference(consumerType.provider)
        for (use in consumerType.uses) {
            markTypeReference(use.service)
            markOperationReference(use.operations, use.node.serviceOperationNames)
        }
    }

    private fun markOperationReference(operations: List<ServiceType.Operation>, operationReferences: List<IdentifierNode>) {
        val opLookup = operations.associateBy { it.name }
        for (operationName in operationReferences) {
            val operation = opLookup[operationName.name] ?: continue
            markOperationReference(operation, operationName)
        }
    }

    protected open fun markOperationReference(operation: ServiceType.Operation, reference: IdentifierNode) {}

    protected open fun markPackageDeclaration(packageDeclaration: PackageDeclarationNode) {}

    protected open fun markImport(import: ImportNode, importedType: Type) {
        markType(import.name, importedType)
    }
}