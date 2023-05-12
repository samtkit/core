package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.common.Location

internal class SemanticModelPostProcessor(private val controller: DiagnosticController) {
    /**
     * Check that types are reasonably used (e.g. cannot use a service as a field within a record)
     * Resolves and checks 'implements' and 'uses' statements to ensure the referenced operations exist
     */
    fun process(global: Package) {
        global.allSubPackages.forEach {
            it.records.forEach(::checkRecord)
            it.services.forEach(::checkService)
        }
        global.allSubPackages.forEach {
            // Important: Provider must be checked after Service!
            it.providers.forEach(::checkProvider)
        }
        global.allSubPackages.forEach {
            // Important: Consumers must be checked after Providers!
            it.consumers.forEach(::checkConsumer)
        }
    }

    private fun checkModelType(typeReference: TypeReference) {
        check(typeReference is ResolvedTypeReference)
        when (val type = typeReference.type) {
            is ServiceType -> {
                controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                    // error message applies to both record fields and return types
                    message("Cannot use service '${type.name}' as type")
                    highlight("service type not allowed here", typeReference.typeNode.location)
                }
            }

            is ProviderType -> {
                controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                    message("Cannot use provider '${type.name}' as type")
                    highlight("provider type not allowed here", typeReference.typeNode.location)
                }
            }

            is PackageType -> {
                controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                    message("Cannot use package '${type.packageName}' as type")
                    highlight("package type not allowed here", typeReference.typeNode.location)
                }
            }

            is ListType -> {
                checkModelType(type.elementType)
            }

            is MapType -> {
                checkModelType(type.keyType)
                checkModelType(type.valueType)
            }

            is AliasType -> {
                val underlyingTypeReference = type.fullyResolvedType ?: return
                val underlyingType = underlyingTypeReference.type
                if (underlyingType is ServiceType || underlyingType is ProviderType || underlyingType is PackageType) {
                    controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                        message("Type alias refers to '${underlyingType.humanReadableName}', which cannot be used in this context")
                        highlight("type alias", typeReference.typeNode.location)
                        highlight("underlying type", underlyingTypeReference.typeNode.location)
                    }
                }

                if (typeReference.isOptional && underlyingTypeReference.isOptional) {
                    controller.getOrCreateContext(typeReference.typeNode.location.source).warn {
                        message("Type alias refers to type which is already optional, ignoring '?'")
                        highlight("duplicate optional", typeReference.fullNode.location)
                        highlight("declared optional here", underlyingTypeReference.fullNode.location)
                    }
                }
            }

            else -> Unit
        }
    }

    private inline fun checkServiceType(typeReference: TypeReference, block: (serviceType: ServiceType) -> Unit) {
        check(typeReference is ResolvedTypeReference)
        checkBlankTypeReference(typeReference, "service")
        when (val type = typeReference.type) {
            is ServiceType -> {
                block(type)
            }

            is AliasType -> {
                val aliasedTypeReference = type.fullyResolvedType ?: return
                checkBlankTypeReference(aliasedTypeReference, "service")
                val aliasedType = aliasedTypeReference.type
                if (aliasedType is ServiceType) {
                    block(aliasedType)
                } else {
                    controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                        message("Expected a service but type alias '${type.name}' points to '${aliasedType.humanReadableName}'")
                        highlight("type alias", typeReference.typeNode.location)
                        highlight("underlying type", aliasedTypeReference.typeNode.location)
                    }
                }
            }

            is UnknownType -> Unit
            else -> {
                controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                    message("Expected a service but got '${type.humanReadableName}'")
                    highlight("illegal type", typeReference.typeNode.location)
                }
            }
        }
    }

    private inline fun checkProviderType(typeReference: TypeReference, block: (providerType: ProviderType) -> Unit) {
        check(typeReference is ResolvedTypeReference)
        checkBlankTypeReference(typeReference, "provider")
        when (val type = typeReference.type) {
            is ProviderType -> {
                block(type)
            }

            is AliasType -> {
                val aliasedTypeReference = type.fullyResolvedType ?: return
                checkBlankTypeReference(aliasedTypeReference, "provider")
                val aliasedType = aliasedTypeReference.type
                if (aliasedType is ProviderType) {
                    block(aliasedType)
                } else {
                    controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                        message("Expected a provider but type alias '${type.name}' points to '${aliasedType.humanReadableName}'")
                        highlight("type alias", typeReference.typeNode.location)
                        highlight("underlying type", aliasedTypeReference.typeNode.location)
                    }
                }
            }

            is UnknownType -> Unit
            else -> {
                controller.getOrCreateContext(typeReference.typeNode.location.source).error {
                    message("Expected a provider but got '${type.humanReadableName}'")
                    highlight("illegal type", typeReference.typeNode.location)
                }
            }
        }
    }

    /** A blank type reference has no constraints or optional marker */
    private fun checkBlankTypeReference(typeReference: ResolvedTypeReference, what: String): Boolean {
        var isBlank = true
        if (typeReference.constraints.isNotEmpty()) {
            isBlank = false
            controller.getOrCreateContext(typeReference.fullNode.location.source).error {
                message("Cannot have constraints on $what")
                for (constraint in typeReference.constraints) {
                    highlight("illegal constraint", constraint.node.location)
                }
            }
        }
        if (typeReference.isOptional) {
            isBlank = false
            controller.getOrCreateContext(typeReference.fullNode.location.source).error {
                message("Cannot have optional $what")
                highlight("illegal optional", typeReference.fullNode.location)
            }
        }
        return isBlank
    }

    private fun checkRecord(record: RecordType) {
        record.fields.forEach { checkModelType(it.type) }
    }

    private fun checkService(service: ServiceType) {
        service.operations.forEach { operation ->
            operation.parameters.forEach { checkModelType(it.type) }
            when (operation) {
                is ServiceType.OnewayOperation -> Unit
                is ServiceType.RequestResponseOperation -> {
                    operation.returnType?.let { checkModelType(it) }
                }
            }
        }
    }

    private fun checkProvider(provider: ProviderType) {
        val implementsTypes = mutableMapOf<ServiceType, Location>()
        provider.implements.forEach { implements ->
            checkServiceType(implements.service) { type ->
                implementsTypes.putIfAbsent(type, implements.node.location)?.let { existingLocation ->
                    controller.getOrCreateContext(implements.node.location.source).error {
                        message("Service '${type.name}' already implemented")
                        highlight("duplicate implements", implements.node.location)
                        highlight("previous implements", existingLocation)
                    }
                    return@forEach
                }

                implements.operations = if (implements.node.serviceOperationNames.isEmpty()) {
                    type.operations
                } else {
                    implements.node.serviceOperationNames.mapNotNull { serviceOperationName ->
                        val matchingOperation = type.operations.find { it.name == serviceOperationName.name }
                        if (matchingOperation != null) {
                            matchingOperation
                        } else {
                            controller.getOrCreateContext(provider.declaration.location.source).error {
                                message("Operation '${serviceOperationName.name}' not found in service '${type.name}'")
                                highlight("unknown operation", serviceOperationName.location)
                            }
                            null
                        }
                    }
                }
            }
        }
    }

    private fun checkConsumer(consumer: ConsumerType) {
        val usesTypes = mutableMapOf<ServiceType, Location>()
        checkProviderType(consumer.provider) { providerType ->
            consumer.uses.forEach { uses ->
                checkServiceType(uses.service) { type ->
                    usesTypes.putIfAbsent(type, uses.node.location)?.let { existingLocation ->
                        controller.getOrCreateContext(uses.node.location.source).error {
                            message("Service '${type.name}' already used")
                            highlight("duplicate uses", uses.node.location)
                            highlight("previous uses", existingLocation)
                        }
                        return@forEach
                    }

                    val matchingImplements =
                        providerType.implements.find { (it.service as ResolvedTypeReference).type == type }
                    if (matchingImplements == null) {
                        controller.getOrCreateContext(uses.node.location.source).error {
                            message("Service '${type.name}' is not implemented by provider '${providerType.name}'")
                            highlight("unavailable service", uses.node.serviceName.location)
                        }
                        return@forEach
                    }
                    uses.operations = if (uses.node.serviceOperationNames.isEmpty()) {
                        matchingImplements.operations
                    } else {
                        uses.node.serviceOperationNames.mapNotNull { serviceOperationName ->
                            val matchingOperation =
                                matchingImplements.operations.find { it.name == serviceOperationName.name }
                            if (matchingOperation != null) {
                                matchingOperation
                            } else {
                                if (type.operations.any { it.name == serviceOperationName.name }) {
                                    controller.getOrCreateContext(uses.node.location.source).error {
                                        message("Operation '${serviceOperationName.name}' in service '${type.name}' is not implemented by provider '${providerType.name}'")
                                        highlight("unavailable operation", serviceOperationName.location)
                                    }
                                } else {
                                    controller.getOrCreateContext(uses.node.location.source).error {
                                        message("Operation '${serviceOperationName.name}' not found in service '${type.name}'")
                                        highlight("unknown operation", serviceOperationName.location)
                                    }
                                }
                                null
                            }
                        }
                    }
                }
            }
        }

    }
}
