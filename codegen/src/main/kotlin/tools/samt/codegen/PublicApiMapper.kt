package tools.samt.codegen

import tools.samt.api.plugin.*
import tools.samt.api.types.*
import tools.samt.common.DiagnosticController
import tools.samt.parser.reportError
import tools.samt.parser.reportInfo
import tools.samt.parser.reportWarning

class PublicApiMapper(
    private val transportParsers: List<TransportConfigurationParser>,
    private val controller: DiagnosticController,
) {
    private val typeCache = mutableMapOf<tools.samt.semantic.Type, Type>()

    /**
     * Returns a lazy delegate that will initialize its value only once, without synchronization.
     * Because we are in a single-threaded environment, this is safe and significantly faster.
     */
    fun <T> unsafeLazy(initializer: () -> T) = lazy(LazyThreadSafetyMode.NONE, initializer)

    fun toPublicApi(samtPackage: tools.samt.semantic.Package) = object : SamtPackage {
        override val name = samtPackage.name
        override val qualifiedName = samtPackage.qualifiedName
        override val records = samtPackage.records.map { it.toPublicRecord() }
        override val enums = samtPackage.enums.map { it.toPublicEnum() }
        override val services = samtPackage.services.map { it.toPublicService() }
        override val providers = samtPackage.providers.map { it.toPublicProvider() }
        override val consumers = samtPackage.consumers.map { it.toPublicConsumer() }
        override val aliases = samtPackage.aliases.map { it.toPublicAlias() }
    }

    private fun tools.samt.semantic.RecordType.toPublicRecord() = object : RecordType {
        override val name get() = this@toPublicRecord.name
        override val qualifiedName by unsafeLazy { this@toPublicRecord.getQualifiedName() }
        override val fields by unsafeLazy { this@toPublicRecord.fields.map { it.toPublicField() } }
    }

    private fun tools.samt.semantic.RecordType.Field.toPublicField() = object : RecordField {
        override val name get() = this@toPublicField.name
        override val type by unsafeLazy { this@toPublicField.type.toPublicTypeReference() }
    }

    private fun tools.samt.semantic.EnumType.toPublicEnum() = object : EnumType {
        override val name get() = this@toPublicEnum.name
        override val qualifiedName by unsafeLazy { this@toPublicEnum.getQualifiedName() }
        override val values get() = this@toPublicEnum.values
    }

    private fun tools.samt.semantic.ServiceType.toPublicService() = object : ServiceType {
        override val name get() = this@toPublicService.name
        override val qualifiedName by unsafeLazy { this@toPublicService.getQualifiedName() }
        override val operations by unsafeLazy { this@toPublicService.operations.map { it.toPublicOperation() } }
    }

    private fun tools.samt.semantic.ServiceType.Operation.toPublicOperation() = when (this) {
        is tools.samt.semantic.ServiceType.OnewayOperation -> toPublicOnewayOperation()
        is tools.samt.semantic.ServiceType.RequestResponseOperation -> toPublicRequestResponseOperation()
    }

    private fun tools.samt.semantic.ServiceType.OnewayOperation.toPublicOnewayOperation() = object : OnewayOperation {
        override val name get() = this@toPublicOnewayOperation.name
        override val parameters by unsafeLazy { this@toPublicOnewayOperation.parameters.map { it.toPublicParameter() } }
    }

    private fun tools.samt.semantic.ServiceType.RequestResponseOperation.toPublicRequestResponseOperation() =
        object : RequestResponseOperation {
            override val name get() = this@toPublicRequestResponseOperation.name
            override val parameters by unsafeLazy { this@toPublicRequestResponseOperation.parameters.map { it.toPublicParameter() } }
            override val returnType by unsafeLazy { this@toPublicRequestResponseOperation.returnType?.toPublicTypeReference() }
            override val isAsync get() = this@toPublicRequestResponseOperation.isAsync
        }

    private fun tools.samt.semantic.ServiceType.Operation.Parameter.toPublicParameter() =
        object : ServiceOperationParameter {
            override val name get() = this@toPublicParameter.name
            override val type by unsafeLazy { this@toPublicParameter.type.toPublicTypeReference() }
        }

    private fun tools.samt.semantic.ProviderType.toPublicProvider() = object : ProviderType {
        override val name get() = this@toPublicProvider.name
        override val qualifiedName by unsafeLazy { this@toPublicProvider.getQualifiedName() }
        override val implements by unsafeLazy { this@toPublicProvider.implements.map { it.toPublicImplements() } }
        override val transport by unsafeLazy { this@toPublicProvider.transport.toPublicTransport(this) }
    }

    private class Params(
        override val config: ConfigurationObject?,
        val controller: DiagnosticController,
    ) : TransportConfigurationParserParams {

        override fun reportError(message: String, context: ConfigurationElement?) {
            if (context != null && context is PublicApiConfigurationMapping) {
                context.original.reportError(controller) {
                    message(message)
                    highlight("offending configuration", context.original.location)
                }
            } else {
                controller.reportGlobalError(message)
            }
        }

        override fun reportWarning(message: String, context: ConfigurationElement?) {
            if (context != null && context is PublicApiConfigurationMapping) {
                context.original.reportWarning(controller) {
                    message(message)
                    highlight("offending configuration", context.original.location)
                }
            } else {
                controller.reportGlobalWarning(message)
            }
        }

        override fun reportInfo(message: String, context: ConfigurationElement?) {
            if (context != null && context is PublicApiConfigurationMapping) {
                context.original.reportInfo(controller) {
                    message(message)
                    highlight("related configuration", context.original.location)
                }
            } else {
                controller.reportGlobalInfo(message)
            }
        }
    }

    private fun tools.samt.semantic.ProviderType.Transport.toPublicTransport(provider: ProviderType): TransportConfiguration {
        val transportConfigurationParsers = transportParsers.filter { it.transportName == name }
        when (transportConfigurationParsers.size) {
            0 -> controller.reportGlobalWarning("No transport configuration parser found for transport '$name'")
            1 -> {
                val transportConfigurationParser = transportConfigurationParsers.single()
                val transportConfigNode =
                    configuration?.let { TransportConfigurationMapper(provider, controller).parse(it) }
                val config = Params(transportConfigNode, controller)
                try {
                    return transportConfigurationParser.parse(config)
                } catch (e: Exception) {
                    controller.reportGlobalError("Failed to parse transport configuration for transport '$name': ${e.message}")
                }
            }

            else -> controller.reportGlobalError("Multiple transport configuration parsers found for transport '$name'")
        }

        return object : TransportConfiguration {
            override val name: String
                get() = this@toPublicTransport.name
        }
    }

    private fun tools.samt.semantic.ProviderType.Implements.toPublicImplements() = object : ProvidedService {
        override val service by unsafeLazy { this@toPublicImplements.service.toPublicTypeReference().type as ServiceType }
        private val implementedOperationNames by unsafeLazy { this@toPublicImplements.operations.mapTo(mutableSetOf()) { it.name } }
        override val implementedOperations by unsafeLazy { service.operations.filter { it.name in implementedOperationNames } }
        override val unimplementedOperations by unsafeLazy { service.operations.filter { it.name !in implementedOperationNames } }
    }

    private fun tools.samt.semantic.ConsumerType.toPublicConsumer() = object : ConsumerType {
        override val provider by unsafeLazy { this@toPublicConsumer.provider.toPublicTypeReference().type as ProviderType }
        override val uses by unsafeLazy { this@toPublicConsumer.uses.map { it.toPublicUses() } }
        override val samtPackage get() = this@toPublicConsumer.parentPackage.qualifiedName
    }

    private fun tools.samt.semantic.ConsumerType.Uses.toPublicUses() = object : ConsumedService {
        override val service by unsafeLazy { this@toPublicUses.service.toPublicTypeReference().type as ServiceType }
        private val consumedOperationNames by unsafeLazy { this@toPublicUses.operations.mapTo(mutableSetOf()) { it.name } }
        override val consumedOperations by unsafeLazy { service.operations.filter { it.name in consumedOperationNames } }
        override val unconsumedOperations by unsafeLazy { service.operations.filter { it.name !in consumedOperationNames } }
    }

    private fun tools.samt.semantic.AliasType.toPublicAlias() = object : AliasType {
        override val name get() = this@toPublicAlias.name
        override val qualifiedName by unsafeLazy { this@toPublicAlias.getQualifiedName() }
        override val aliasedType by unsafeLazy { this@toPublicAlias.aliasedType.toPublicTypeReference() }
        override val runtimeType by unsafeLazy { this@toPublicAlias.fullyResolvedType.toPublicTypeReference() }
    }

    private inline fun <reified T : tools.samt.semantic.ResolvedTypeReference.Constraint> List<tools.samt.semantic.ResolvedTypeReference.Constraint>.findConstraint() =
        firstOrNull { it is T } as T?

    private fun tools.samt.semantic.TypeReference?.toPublicTypeReference(): TypeReference {
        check(this is tools.samt.semantic.ResolvedTypeReference)
        val typeReference: tools.samt.semantic.ResolvedTypeReference = this@toPublicTypeReference
        val runtimeTypeReference = when (val type = typeReference.type) {
            is tools.samt.semantic.AliasType -> checkNotNull(type.fullyResolvedType) { "Found unresolved alias when generating code" }
            else -> typeReference
        }
        return object : TypeReference {
            override val type by lazy { typeReference.type.toPublicType() }
            override val isOptional get() = typeReference.isOptional
            override val rangeConstraint by unsafeLazy {
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Range>()
                    ?.toPublicRangeConstraint()
            }
            override val sizeConstraint by unsafeLazy {
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Size>()
                    ?.toPublicSizeConstraint()
            }
            override val patternConstraint by unsafeLazy {
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern>()
                    ?.toPublicPatternConstraint()
            }
            override val valueConstraint by unsafeLazy {
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Value>()
                    ?.toPublicValueConstraint()
            }

            override val runtimeType by unsafeLazy { runtimeTypeReference.type.toPublicType() }
            override val isRuntimeOptional get() = isOptional || runtimeTypeReference.isOptional
            override val runtimeRangeConstraint by unsafeLazy {
                rangeConstraint
                    ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Range>()
                        ?.toPublicRangeConstraint()
            }
            override val runtimeSizeConstraint by unsafeLazy {
                sizeConstraint
                    ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Size>()
                        ?.toPublicSizeConstraint()
            }
            override val runtimePatternConstraint by unsafeLazy {
                patternConstraint
                    ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern>()
                        ?.toPublicPatternConstraint()
            }
            override val runtimeValueConstraint by unsafeLazy {
                valueConstraint
                    ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Value>()
                        ?.toPublicValueConstraint()
            }
        }
    }

    private fun tools.samt.semantic.Type.toPublicType() = typeCache.computeIfAbsent(this@toPublicType) {
        when (this) {
            tools.samt.semantic.IntType -> object : IntType {}
            tools.samt.semantic.LongType -> object : LongType {}
            tools.samt.semantic.FloatType -> object : FloatType {}
            tools.samt.semantic.DoubleType -> object : DoubleType {}
            tools.samt.semantic.DecimalType -> object : DecimalType {}
            tools.samt.semantic.BooleanType -> object : BooleanType {}
            tools.samt.semantic.StringType -> object : StringType {}
            tools.samt.semantic.BytesType -> object : BytesType {}
            tools.samt.semantic.DateType -> object : DateType {}
            tools.samt.semantic.DateTimeType -> object : DateTimeType {}
            tools.samt.semantic.DurationType -> object : DurationType {}
            is tools.samt.semantic.ListType -> object : ListType {
                override val elementType by unsafeLazy { this@toPublicType.elementType.toPublicTypeReference() }
            }

            is tools.samt.semantic.MapType -> object : MapType {
                override val keyType by unsafeLazy { this@toPublicType.keyType.toPublicTypeReference() }
                override val valueType by unsafeLazy { this@toPublicType.valueType.toPublicTypeReference() }
            }

            is tools.samt.semantic.AliasType -> toPublicAlias()
            is tools.samt.semantic.ConsumerType -> toPublicConsumer()
            is tools.samt.semantic.EnumType -> toPublicEnum()
            is tools.samt.semantic.ProviderType -> toPublicProvider()
            is tools.samt.semantic.RecordType -> toPublicRecord()
            is tools.samt.semantic.ServiceType -> toPublicService()
            is tools.samt.semantic.PackageType -> error("Package type cannot be converted to public API")
            tools.samt.semantic.UnknownType -> error("Unknown type cannot be converted to public API")
        }
    }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Range.toPublicRangeConstraint() =
        object : Constraint.Range {
            override val lowerBound get() = this@toPublicRangeConstraint.lowerBound
            override val upperBound get() = this@toPublicRangeConstraint.upperBound
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Size.toPublicSizeConstraint() =
        object : Constraint.Size {
            override val lowerBound get() = this@toPublicSizeConstraint.lowerBound
            override val upperBound get() = this@toPublicSizeConstraint.upperBound
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern.toPublicPatternConstraint() =
        object : Constraint.Pattern {
            override val pattern get() = this@toPublicPatternConstraint.pattern
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Value.toPublicValueConstraint() =
        object : Constraint.Value {
            override val value get() = this@toPublicValueConstraint.value
        }

    private fun tools.samt.semantic.UserDeclaredNamedType.getQualifiedName(): String {
        return if (parentPackage.qualifiedName.isEmpty()) {
            name
        } else {
            "${parentPackage.qualifiedName}.$name"
        }
    }
}
