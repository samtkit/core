package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.common.Location
import tools.samt.common.SourceFile
import tools.samt.parser.*

/**
 * Goals of the semantic model:
 * - Model the entire package structure of the project
 * - Model the types and their relationships
 * - Resolve all references to types
 * - Resolve all references to their declarations in the AST
 * */
class SemanticModelBuilder(
    private val files: List<FileNode>,
    private val controller: DiagnosticController,
) {
    private val global = Package(name = "")
    private val constraintBuilder = ConstraintBuilder(controller)
    private val postProcessor = SemanticModelPostProcessor(controller)

    private inline fun ensureNameIsAvailable(
        parentPackage: Package,
        statement: NamedDeclarationNode,
        block: () -> Unit,
    ) {
        if (statement.name !in parentPackage) {
            block()
        } else {
            val existingType = parentPackage.types.getValue(statement.name.name)
            controller.createContext(statement.location.source).error {
                message("'${statement.name.name}' is already declared")
                highlight("duplicate declaration", statement.name.location)
                if (existingType is UserDefinedType) {
                    highlight("previous declaration", existingType.definition.location)
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
                controller.createContext(item.location.source).error {
                    message("$what '$name' is defined more than once")
                    highlight("duplicate declaration", identifierGetter(item).location)
                    highlight("previous declaration", existingLocation)
                }
            }
        }
    }

    private fun build(): Package {
        buildPackages()

        val fileScopeBySource = files.associate { it.sourceFile to createFileScope(it) }

        resolveTypes(fileScopeBySource)

        postProcessor.process(global)

        return global
    }

    private fun buildPackages() {
        for (file in files) {
            var parentPackage = global
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
                        ensureNameIsAvailable(parentPackage, statement) {
                            reportDuplicates(statement.fields, "Record field") { it.name }
                            if (statement.extends.isNotEmpty()) {
                                controller.createContext(statement.location.source).error {
                                    message("Record extends are not yet supported")
                                    highlight("cannot extend other records", statement.extends.first().location)
                                }
                            }
                            val fields = statement.fields.map { field ->
                                RecordType.Field(field.name.name, UnresolvedTypeReference(field.type))
                            }
                            parentPackage += RecordType(statement.name.name, fields, statement)
                        }
                    }

                    is EnumDeclarationNode -> {
                        ensureNameIsAvailable(parentPackage, statement) {
                            reportDuplicates(statement.values, "Enum value") { it }
                            val values = statement.values.map { it.name }
                            parentPackage += EnumType(statement.name.name, values, statement)
                        }
                    }

                    is ServiceDeclarationNode -> {
                        ensureNameIsAvailable(parentPackage, statement) {
                            reportDuplicates(statement.operations, "Operation") { it.name }
                            val operations = statement.operations.map { operation ->
                                reportDuplicates(operation.parameters, "Parameter") { it.name }
                                val parameters = operation.parameters.map { parameter ->
                                    ServiceType.Operation.Parameter(
                                        name = parameter.name.name,
                                        type = UnresolvedTypeReference(parameter.type)
                                    )
                                }
                                when (operation) {
                                    is OnewayOperationNode -> {
                                        ServiceType.OnewayOperation(
                                            name = operation.name.name,
                                            parameters = parameters,
                                        )
                                    }

                                    is RequestResponseOperationNode -> {
                                        if (operation.isAsync) {
                                            controller.createContext(operation.location.source).error {
                                                message("Async operations are not yet supported")
                                                highlight("unsupported async operation", operation.location)
                                            }
                                        }
                                        ServiceType.RequestResponseOperation(
                                            name = operation.name.name,
                                            parameters = parameters,
                                            returnType = operation.returnType?.let { UnresolvedTypeReference(it) },
                                            raisesTypes = operation.raises.map { UnresolvedTypeReference(it) },
                                        )
                                    }
                                }
                            }
                            parentPackage += ServiceType(statement.name.name, operations, statement)
                        }
                    }

                    is ProviderDeclarationNode -> {
                        ensureNameIsAvailable(parentPackage, statement) {
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
                    }

                    is ConsumerDeclarationNode -> {
                        parentPackage += ConsumerType(
                            provider = UnresolvedTypeReference(statement.providerName),
                            uses = statement.usages.map {
                                ConsumerType.Uses(
                                    service = UnresolvedTypeReference(it.serviceName),
                                    operations = emptyList(),
                                    definition = it
                                )
                            },
                            definition = statement
                        )
                    }

                    is TypeAliasNode -> {
                        controller.createContext(statement.location.source).error {
                            message("Type aliases are not yet supported")
                            highlight("unsupported feature", statement.location)
                        }
                    }

                    is PackageDeclarationNode,
                    is ImportNode,
                    -> Unit
                }
            }
        }
    }

    private fun resolveTypes(fileScopeBySource: Map<SourceFile, FileScope>) {
        fun TypeReference.resolve(): ResolvedTypeReference {
            check(this is UnresolvedTypeReference) { "Type reference must be unresolved" }

            return resolveExpression(fileScopeBySource, expression)
        }

        for (subPackage in global.allSubPackages) {
            for (record in subPackage.records) {
                for (field in record.fields) {
                    field.type = field.type.resolve()
                }
            }
            for (service in subPackage.services) {
                for (operation in service.operations) {
                    for (parameter in operation.parameters) {
                        parameter.type = parameter.type.resolve()
                    }
                    when (operation) {
                        is ServiceType.OnewayOperation -> Unit
                        is ServiceType.RequestResponseOperation -> {
                            operation.raisesTypes = operation.raisesTypes.map { it.resolve() }
                            operation.returnType = operation.returnType?.resolve()
                        }
                    }
                }
            }
            for (provider in subPackage.providers) {
                for (implement in provider.implements) {
                    implement.service = implement.service.resolve()
                }
            }
            for (consumer in subPackage.consumers) {
                consumer.provider = consumer.provider.resolve()
                for (uses in consumer.uses) {
                    uses.service = uses.service.resolve()
                }
            }
        }
    }

    private fun resolveType(bundleIdentifierNode: BundleIdentifierNode) = resolveType(bundleIdentifierNode.components)
    private fun resolveType(components: List<IdentifierNode>, start: Package = global): Type? {
        var currentPackage = start
        val iterator = components.listIterator()
        while (iterator.hasNext()) {
            val component = iterator.next()
            when (val resolvedType = currentPackage.resolveType(component)) {
                is PackageType -> {
                    currentPackage = resolvedType.sourcePackage
                }

                null -> {
                    controller.createContext(component.location.source).error {
                        message("Could not resolve reference '${component.name}'")
                        highlight("unresolved reference", component.location)
                    }
                    return null
                }

                else -> {
                    if (iterator.hasNext()) {
                        // We resolved a non-package type but there are still components left

                        controller.createContext(component.location.source).error {
                            message("Type '${component.name}' is not a package, cannot access sub-types")
                            highlight("must be a package", component.location)
                        }
                        return null
                    }
                    return resolvedType
                }
            }
        }

        return PackageType(currentPackage)
    }

    data class FileScope(val filePackage: PackageType, val typeLookup: Map<String, Type>)

    private fun createFileScope(file: FileNode): FileScope {
        // Add all types from the file package
        val filePackage = resolveType(file.packageDeclaration.name)
        check(filePackage is PackageType)

        val typeLookup: Map<String, Type> = buildMap {
            putAll(filePackage.sourcePackage.types)

            // Add all imports to scope
            file.imports.forEach { import ->
                fun addImportedType(name: String, type: Type) {
                    putIfAbsent(name, type)?.let { existingType ->
                        controller.createContext(file.sourceFile).error {
                            message("Import '$name' conflicts with locally defined type with same name")
                            highlight("conflicting import", import.location)
                            if (existingType is UserDefinedType) {
                                highlight("local type with same name", existingType.definition.location)
                            }
                        }
                    }
                }
                when (import) {
                    is TypeImportNode -> {
                        // Just import one type
                        val type = resolveType(import.name)
                        if (type != null) {
                            val name = if (import.alias != null) {
                                import.alias!!.name
                            } else {
                                import.name.components.last().name
                            }

                            addImportedType(name, type)
                        }
                    }

                    is WildcardImportNode -> {
                        // Import all types from the package
                        val type = resolveType(import.name)
                        if (type != null) {
                            if (type is PackageType) {
                                type.sourcePackage.types.forEach { (name, type) ->
                                    addImportedType(name, type)
                                }
                            } else {
                                controller.createContext(file.sourceFile).error {
                                    message("Import '${import.name.name}.*' must point to a package and not a type")
                                    highlight(
                                        "illegal wildcard import", import.location, suggestChange = "import ${
                                            import.name.components.dropLast(1).joinToString(".") { it.name }
                                        }.*")
                                }
                            }
                        }
                    }
                }
            }

            // Add all top-level packages to scope
            val topLevelPackages = global.subPackages.map { PackageType(it) }
            topLevelPackages.forEach { putIfAbsent(it.packageName, it) }

            // Add built-in types
            fun addBuiltIn(name: String, type: Type) {
                putIfAbsent(name, type)?.let { existingType ->
                    controller.createContext(file.sourceFile).error {
                        message("Type '$name' shadows built-in type with same name")
                        if (existingType is UserDefinedType) {
                            val definition = existingType.definition
                            if (definition is NamedDeclarationNode) {
                                highlight("Shadows built-in type", definition.name.location)
                            } else {
                                highlight("Shadows built-in type '$name'", definition.location)
                            }
                        }
                    }
                }
            }
            addBuiltIn("Int", IntType)
            addBuiltIn("Long", LongType)
            addBuiltIn("Float", FloatType)
            addBuiltIn("Double", DoubleType)
            addBuiltIn("Decimal", DecimalType)
            addBuiltIn("Boolean", BooleanType)
            addBuiltIn("String", StringType)
            addBuiltIn("Bytes", BytesType)
            addBuiltIn("Date", DateType)
            addBuiltIn("DateTime", DateTimeType)
            addBuiltIn("Duration", DurationType)
        }

        return FileScope(filePackage, typeLookup)
    }

    private fun resolveExpression(
        fileScopes: Map<SourceFile, FileScope>,
        rootExpression: ExpressionNode,
    ): ResolvedTypeReference {
        fun resolveExpression(expression: ExpressionNode): ResolvedTypeReference {
            val scope = fileScopes.getValue(expression.location.source)
            when (expression) {
                is IdentifierNode -> {
                    scope.typeLookup[expression.name]?.let {
                        return ResolvedTypeReference(expression, it)
                    }

                    controller.createContext(expression.location.source).error {
                        message("Type '${expression.name}' could not be resolved")
                        highlight("unresolved type", expression.location)
                    }
                }

                is BundleIdentifierNode -> {
                    // Bundle identifiers with one component are treated like normal identifiers
                    if (expression.components.size == 1) {
                        return resolveExpression(expression.components.first())
                    }
                    // Type is foo.bar.Baz
                    // Resolve foo first, it must be a package
                    when (val expectedPackageType = scope.typeLookup[expression.components.first().name]) {
                        is PackageType -> {
                            resolveType(
                                expression.components.subList(1, expression.components.size),
                                expectedPackageType.sourcePackage
                            )?.let {
                                return ResolvedTypeReference(expression, it)
                            }
                        }

                        null -> {
                            controller.createContext(expression.location.source).error {
                                message("Type '${expression.name}' could not be resolved")
                                highlight("unresolved type", expression.location)
                            }
                        }

                        else -> {
                            controller.createContext(expression.location.source).error {
                                message("Type '${expression.components.first().name}' is not a package, cannot access sub-types")
                                highlight("not a package", expression.components.first().location)
                            }
                        }
                    }
                }

                is CallExpressionNode -> {
                    val baseType = resolveExpression(expression.base)
                    val constraints = expression.arguments.mapNotNull { constraintBuilder.build(baseType.type, it) }
                    if (baseType.constraints.isNotEmpty()) {
                        controller.createContext(expression.location.source).error {
                            message("Cannot have nested constraints")
                            highlight("illegal nested constraint", expression.location)
                        }
                    }
                    return baseType.copy(constraints = constraints)
                }

                is GenericSpecializationNode -> {
                    val name = expression.base.let {
                        when (it) {
                            is IdentifierNode -> it.name
                            is BundleIdentifierNode -> it.name
                            else -> null
                        }
                    }
                    when (name) {
                        "List" -> {
                            if (expression.arguments.size == 1) {
                                return ResolvedTypeReference(
                                    expression,
                                    ListType(resolveExpression(expression.arguments[0]))
                                )
                            }
                        }

                        "Map" -> {
                            if (expression.arguments.size == 2) {
                                return ResolvedTypeReference(
                                    expression,
                                    MapType(
                                        keyType = resolveExpression(expression.arguments[0]),
                                        valueType = resolveExpression(expression.arguments[1])
                                    )
                                )
                            }
                        }
                    }
                    controller.createContext(expression.location.source).error {
                        message("Unsupported generic type")
                        highlight(expression.location)
                        help("Valid generic types are List<Value> and Map<Key, Value>")
                    }
                }

                is OptionalDeclarationNode -> {
                    val baseType = resolveExpression(expression.base)
                    if (baseType.isOptional) {
                        controller.createContext(expression.location.source).warn {
                            message("Type is already optional, ignoring '?'")
                            highlight("already optional", expression.base.location)
                        }
                    }
                    return baseType.copy(isOptional = true)
                }

                is BooleanNode,
                is NumberNode,
                is StringNode,
                -> controller.createContext(expression.location.source).error {
                    message("Cannot use literal value as type")
                    highlight("not a type expression", expression.location)
                }

                is ObjectNode,
                is ArrayNode,
                is RangeExpressionNode,
                is WildcardNode,
                -> controller.createContext(expression.location.source).error {
                    message("Invalid type expression")
                    highlight("not a type expression", expression.location)
                }
            }

            return ResolvedTypeReference(expression, UnknownType)
        }

        return resolveExpression(rootExpression)
    }

    companion object {
        fun build(files: List<FileNode>, controller: DiagnosticController): Package {
            // Sort by path to ensure deterministic order
            return SemanticModelBuilder(files.sortedBy { it.sourceFile.absolutePath }, controller).build()
        }
    }
}
