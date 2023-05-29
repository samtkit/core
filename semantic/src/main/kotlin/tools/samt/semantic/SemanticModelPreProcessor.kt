package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.common.Location
import tools.samt.parser.*

internal class SemanticModelPreProcessor(private val controller: DiagnosticController) {

    private fun reportDuplicateDeclaration(
        parentPackage: Package,
        statement: NamedDeclarationNode,
    ) {
        if (statement.name in parentPackage) {
            val existingType = parentPackage.types.getValue(statement.name.name)
            statement.reportError(controller) {
                message("'${statement.name.name}' is already declared")
                highlight("duplicate declaration", statement.name.location)
                if (existingType is UserDeclared) {
                    highlight("previous declaration", existingType.declaration.location)
                }
            }
        }
    }

    private inline fun <T : Node> reportDuplicates(
        items: List<T>,
        what: String,
        identifierGetter: (node: T) -> IdentifierNode,
    ) {
        val existingItems = mutableMapOf<String, Location>()
        for (item in items) {
            val name = identifierGetter(item).name
            val existingLocation = existingItems.putIfAbsent(name, item.location)
            if (existingLocation != null) {
                item.reportError(controller) {
                    message("$what '$name' is defined more than once")
                    highlight("duplicate declaration", identifierGetter(item).location)
                    highlight("previous declaration", existingLocation)
                }
            }
        }
    }

    private fun reportFileSeparation(file: FileNode) {
        val statements = file.statements
        if (statements.size <= 1) {
            return
        }
        for (provider in statements.filterIsInstance<ProviderDeclarationNode>()) {
            controller.getOrCreateContext(provider.location.source).warn {
                message("Provider declaration should be in its own file")
                highlight("provider declaration", provider.location, highlightBeginningOnly = true)
            }
        }
        for (consumer in statements.filterIsInstance<ConsumerDeclarationNode>()) {
            controller.getOrCreateContext(consumer.location.source).warn {
                message("Consumer declaration should be in its own file")
                highlight("consumer declaration", consumer.location, highlightBeginningOnly = true)
            }
        }
    }

    fun fillPackage(samtPackage: Package, files: List<FileNode>) {
        for (file in files) {
            reportFileSeparation(file)

            var parentPackage = samtPackage
            for (component in file.packageDeclaration.name.components) {
                var subPackage = parentPackage.subPackages.find { it.name == component.name }
                if (subPackage == null) {
                    subPackage = Package(component.name, parentPackage)
                    parentPackage.subPackages.add(subPackage)
                }
                parentPackage = subPackage
            }

            for (statement in file.statements) {
                when (statement) {
                    is RecordDeclarationNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        reportDuplicates(statement.fields, "Record field") { it.name }
                        if (statement.extends.isNotEmpty()) {
                            statement.reportError(controller) {
                                message("Record extends are not yet supported")
                                highlight("cannot extend other records", statement.extends.first().location)
                            }
                        }
                        val fields = statement.fields.map { field ->
                            RecordType.Field(
                                name = field.name.name,
                                type = UnresolvedTypeReference(field.type),
                                declaration = field
                            )
                        }
                        parentPackage += RecordType(
                            fields = fields,
                            declaration = statement,
                            parentPackage = parentPackage,
                        )
                    }

                    is EnumDeclarationNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        reportDuplicates(statement.values, "Enum value") { it }
                        val values = statement.values.map { it.name }
                        parentPackage += EnumType(values, statement, parentPackage)
                    }

                    is ServiceDeclarationNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        reportDuplicates(statement.operations, "Operation") { it.name }
                        val operations = statement.operations.map { operation ->
                            reportDuplicates(operation.parameters, "Parameter") { it.name }
                            val parameters = operation.parameters.map { parameter ->
                                ServiceType.Operation.Parameter(
                                    name = parameter.name.name,
                                    type = UnresolvedTypeReference(parameter.type),
                                    declaration = parameter,
                                )
                            }
                            when (operation) {
                                is OnewayOperationNode -> {
                                    ServiceType.OnewayOperation(
                                        name = operation.name.name,
                                        parameters = parameters,
                                        declaration = operation,
                                    )
                                }

                                is RequestResponseOperationNode -> {
                                    ServiceType.RequestResponseOperation(
                                        name = operation.name.name,
                                        parameters = parameters,
                                        declaration = operation,
                                        returnType = operation.returnType?.let { UnresolvedTypeReference(it) },
                                        raisesTypes = operation.raises.map { UnresolvedTypeReference(it) },
                                        isAsync = operation.isAsync,
                                    )
                                }
                            }
                        }
                        parentPackage += ServiceType(operations, statement, parentPackage)
                    }

                    is ProviderDeclarationNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        val implements = statement.implements.map { implements ->
                            ProviderType.Implements(
                                UnresolvedTypeReference(implements.serviceName),
                                emptyList(),
                                implements
                            )
                        }

                        val transport = ProviderType.Transport(
                            name = statement.transport.protocolName.name.lowercase(),
                            configuration = statement.transport.configuration
                        )
                        parentPackage += ProviderType(implements, transport, statement, parentPackage)
                    }

                    is ConsumerDeclarationNode -> {
                        parentPackage += ConsumerType(
                            provider = UnresolvedTypeReference(statement.providerName),
                            uses = statement.usages.map {
                                ConsumerType.Uses(
                                    service = UnresolvedTypeReference(it.serviceName),
                                    operations = emptyList(),
                                    node = it
                                )
                            },
                            declaration = statement,
                            parentPackage = parentPackage,
                        )
                    }

                    is TypeAliasNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        parentPackage += AliasType(
                            aliasedType = UnresolvedTypeReference(statement.type),
                            declaration = statement,
                            parentPackage = parentPackage,
                        )
                    }

                    is PackageDeclarationNode,
                    is ImportNode,
                    -> Unit
                }
            }
        }
    }
}
