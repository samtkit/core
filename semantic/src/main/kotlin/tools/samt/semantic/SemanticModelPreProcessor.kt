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
            controller.getOrCreateContext(statement.location.source).error {
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
                controller.getOrCreateContext(item.location.source).error {
                    message("$what '$name' is defined more than once")
                    highlight("duplicate declaration", identifierGetter(item).location)
                    highlight("previous declaration", existingLocation)
                }
            }
        }
    }

    fun fillPackage(samtPackage: Package, files: List<FileNode>) {
        for (file in files) {
            var parentPackage = samtPackage
            for (component in file.packageDeclaration.name.components) {
                var subPackage = parentPackage.subPackages.find { it.name == component.name }
                if (subPackage == null) {
                    subPackage = Package(component.name)
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
                            controller.getOrCreateContext(statement.location.source).error {
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
                            name = statement.name.name,
                            fields = fields,
                            declaration = statement
                        )
                    }

                    is EnumDeclarationNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        reportDuplicates(statement.values, "Enum value") { it }
                        val values = statement.values.map { it.name }
                        parentPackage += EnumType(statement.name.name, values, statement)
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
                                    if (operation.isAsync) {
                                        controller.getOrCreateContext(operation.location.source).error {
                                            message("Async operations are not yet supported")
                                            highlight("unsupported async operation", operation.location)
                                        }
                                    }
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
                        parentPackage += ServiceType(statement.name.name, operations, statement)
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
                            name = statement.transport.protocolName.name,
                            configuration = statement.transport.configuration
                        )
                        parentPackage += ProviderType(statement.name.name, implements, transport, statement)
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
                            declaration = statement
                        )
                    }

                    is TypeAliasNode -> {
                        reportDuplicateDeclaration(parentPackage, statement)
                        parentPackage += AliasType(
                            name = statement.name.name,
                            aliasedType = UnresolvedTypeReference(statement.type),
                            declaration = statement
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
