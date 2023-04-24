package tools.samt.semantic

import tools.samt.common.DiagnosticController

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
                controller.createContext(typeReference.definition.location.source).error {
                    message("Cannot use service '${type.name}' within data model")
                    highlight("illegal service", typeReference.definition.location)
                }
            }

            is ProviderType -> {
                controller.createContext(typeReference.definition.location.source).error {
                    message("Cannot use provider '${type.name}' within data model")
                    highlight("illegal provider", typeReference.definition.location)
                }
            }

            is PackageType -> {
                controller.createContext(typeReference.definition.location.source).error {
                    message("Cannot use package '${type.packageName}' within data model")
                    highlight("illegal package", typeReference.definition.location)
                }
            }

            is ListType -> {
                checkModelType(type.elementType)
            }

            is MapType -> {
                checkModelType(type.keyType)
                checkModelType(type.valueType)
            }

            else -> Unit
        }
    }

    private inline fun checkServiceType(typeReference: TypeReference, block: (serviceType: ServiceType) -> Unit) {
        check(typeReference is ResolvedTypeReference)
        if (typeReference.constraints.isNotEmpty()) {
            controller.createContext(typeReference.definition.location.source).error {
                message("Cannot have constraints on service")
                for (constraint in typeReference.constraints) {
                    highlight("illegal constraint", constraint.definition.location)
                }
            }
        }
        if (typeReference.isOptional) {
            controller.createContext(typeReference.definition.location.source).error {
                message("Cannot have optional service")
                highlight("illegal optional", typeReference.definition.location)
            }
        }
        when (val type = typeReference.type) {
            is ServiceType -> {
                block(type)
            }

            is UnknownType -> Unit
            else -> {
                controller.createContext(typeReference.definition.location.source).error {
                    message("Expected a service but got '${type.humanReadableName}'")
                    highlight("illegal type", typeReference.definition.location)
                }
            }
        }
    }

    private inline fun checkProviderType(typeReference: TypeReference, block: (providerType: ProviderType) -> Unit) {
        check(typeReference is ResolvedTypeReference)
        if (typeReference.constraints.isNotEmpty()) {
            controller.createContext(typeReference.definition.location.source).error {
                message("Cannot have constraints on provider")
                for (constraint in typeReference.constraints) {
                    highlight("illegal constraint", constraint.definition.location)
                }
            }
        }
        if (typeReference.isOptional) {
            controller.createContext(typeReference.definition.location.source).error {
                message("Cannot have optional provider")
                highlight("illegal optional", typeReference.definition.location)
            }
        }
        when (val type = typeReference.type) {
            is ProviderType -> {
                block(type)
            }

            is UnknownType -> Unit
            else -> {
                controller.createContext(typeReference.definition.location.source).error {
                    message("Expected a provider but got '${type.humanReadableName}'")
                    highlight("illegal type", typeReference.definition.location)
                }
            }
        }
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
        provider.implements.forEach { implements ->
            checkServiceType(implements.service) { type ->
                implements.operations = if (implements.definition.serviceOperationNames.isEmpty()) {
                    type.operations
                } else {
                    implements.definition.serviceOperationNames.mapNotNull { serviceOperationName ->
                        val matchingOperation = type.operations.find { it.name == serviceOperationName.name }
                        if (matchingOperation != null) {
                            matchingOperation
                        } else {
                            controller.createContext(provider.definition.location.source).error {
                                message("Missing operation '${serviceOperationName.name}' in service '${type.name}'")
                                highlight("missing operation", serviceOperationName.location)
                            }
                            null
                        }
                    }
                }
            }
        }
    }

    private fun checkConsumer(consumer: ConsumerType) {
        checkProviderType(consumer.provider) { providerType ->
            consumer.uses.forEach { uses ->
                checkServiceType(uses.service) { type ->
                    val matchingImplements =
                        providerType.implements.find { (it.service as ResolvedTypeReference).type == type }
                    if (matchingImplements == null) {
                        controller.createContext(uses.definition.location.source).error {
                            message("Service '${type.name}' is not implemented by provider '${providerType.name}'")
                            highlight("unavailable service", uses.definition.serviceName.location)
                        }
                        return@forEach
                    }
                    uses.operations = if (uses.definition.serviceOperationNames.isEmpty()) {
                        matchingImplements.operations
                    } else {
                        uses.definition.serviceOperationNames.mapNotNull { serviceOperationName ->
                            val matchingOperation =
                                matchingImplements.operations.find { it.name == serviceOperationName.name }
                            if (matchingOperation != null) {
                                matchingOperation
                            } else {
                                if (type.operations.any { it.name == serviceOperationName.name }) {
                                    controller.createContext(uses.definition.location.source).error {
                                        message("Operation '${serviceOperationName.name}' in service '${type.name}' is not implemented by provider '${providerType.name}'")
                                        highlight("unavailable operation", serviceOperationName.location)
                                    }
                                } else {
                                    controller.createContext(uses.definition.location.source).error {
                                        message("Missing operation '${serviceOperationName.name}' in service '${type.name}'")
                                        highlight("missing operation", serviceOperationName.location)
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
