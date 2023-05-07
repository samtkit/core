package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.parser.*

/**
 * Goals of the semantic model:
 * - Model the entire package structure of the project
 * - Model the types and their relationships
 * - Resolve all references to types
 * - Resolve all references to their declarations in the AST
 * */
class SemanticModelBuilder private constructor(
    private val files: List<FileNode>,
    private val controller: DiagnosticController,
) {
    private val global = Package(name = "")
    private val preProcessor = SemanticModelPreProcessor(controller)
    private val postProcessor = SemanticModelPostProcessor(controller)
    private val referenceResolver = SemanticModelReferenceResolver(controller, global)

    private fun build(): Package {
        preProcessor.fillPackage(global, files)

        val fileScopeBySource = files.associate { it.sourceFile to createFileScope(it) }

        resolveTypes(fileScopeBySource)

        postProcessor.process(global)

        return global
    }

    private fun resolveTypes(fileScopeBySource: Map<SourceFile, FileScope>) {
        fun TypeReference.resolve(): ResolvedTypeReference {
            check(this is UnresolvedTypeReference) { "Type reference must be unresolved" }

            val fileScope = fileScopeBySource.getValue(expression.location.source)
            return referenceResolver.resolveAndLinkExpression(fileScope, expression)
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

    data class FileScope(val filePackage: PackageType, val typeLookup: Map<String, Type>)

    private fun createFileScope(file: FileNode): FileScope {
        val filePackage = referenceResolver.resolveType(file.packageDeclaration.name)
        check(filePackage is PackageType)

        val typeLookup: Map<String, Type> = buildMap {
            // Add all types from the file package
            putAll(filePackage.sourcePackage.types)

            // Add all imports to scope
            file.imports.forEach { import ->
                fun addImportedType(name: String, type: Type) {
                    putIfAbsent(name, type)?.let { existingType ->
                        controller.getOrCreateContext(file.sourceFile).error {
                            message("Import '$name' conflicts with locally defined type with same name")
                            highlight("conflicting import", import.location)
                            if (existingType is UserDeclared) {
                                highlight("local type with same name", existingType.declaration.location)
                            }
                        }
                    }
                }
                when (import) {
                    is TypeImportNode -> {
                        // Just import one type
                        val type = referenceResolver.resolveType(import.name)
                        if (type != null) {
                            filePackage.sourcePackage.linkType(import, type)

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
                        val type = referenceResolver.resolveType(import.name)
                        if (type != null) {
                            filePackage.sourcePackage.linkType(import, type)
                            if (type is PackageType) {
                                type.sourcePackage.types.forEach { (name, type) ->
                                    addImportedType(name, type)
                                }
                            } else {
                                controller.getOrCreateContext(file.sourceFile).error {
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
                    controller.getOrCreateContext(file.sourceFile).error {
                        message("Type '$name' shadows built-in type with same name")
                        if (existingType is UserDeclared) {
                            val definition = existingType.declaration
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

    companion object {
        fun build(files: List<FileNode>, controller: DiagnosticController): Package {
            // Sort by path to ensure deterministic order
            return SemanticModelBuilder(files.sortedBy { it.sourceFile.path }, controller).build()
        }
    }
}
