package tools.samt.codegen

import tools.samt.common.DiagnosticController

class PublicApiMapper(
    private val transportParsers: List<TransportConfigurationParser>,
    private val controller: DiagnosticController,
) {
    fun toPublicApi(samtPackage: tools.samt.semantic.Package) = object : SamtPackage {
        override val name = samtPackage.name
        override val qualifiedName = samtPackage.nameComponents.joinToString(".")
        override val records = samtPackage.records.map { it.toPublicRecord() }
        override val enums = samtPackage.enums.map { it.toPublicEnum() }
        override val services = samtPackage.services.map { it.toPublicService() }
        override val providers = samtPackage.providers.map { it.toPublicProvider() }
        override val consumers = samtPackage.consumers.map { it.toPublicConsumer() }
        override val aliases = samtPackage.aliases.map { it.toPublicAlias() }
    }

    private fun tools.samt.semantic.RecordType.toPublicRecord() = object : RecordType {
        override val name = this@toPublicRecord.name
        override val qualifiedName = this@toPublicRecord.getQualifiedName()
        override val fields = this@toPublicRecord.fields.map { it.toPublicField() }
    }

    private fun tools.samt.semantic.RecordType.Field.toPublicField() = object : RecordField {
        override val name = this@toPublicField.name
        override val type = this@toPublicField.type.toPublicTypeReference()
    }

    private fun tools.samt.semantic.EnumType.toPublicEnum() = object : EnumType {
        override val name = this@toPublicEnum.name
        override val qualifiedName = this@toPublicEnum.getQualifiedName()
        override val values = this@toPublicEnum.values
    }

    private fun tools.samt.semantic.ServiceType.toPublicService() = object : ServiceType {
        override val name = this@toPublicService.name
        override val qualifiedName = this@toPublicService.getQualifiedName()
        override val operations = this@toPublicService.operations.map { it.toPublicOperation() }
    }

    private fun tools.samt.semantic.ServiceType.Operation.toPublicOperation() = when (this) {
        is tools.samt.semantic.ServiceType.OnewayOperation -> toPublicOnewayOperation()
        is tools.samt.semantic.ServiceType.RequestResponseOperation -> toPublicRequestResponseOperation()
    }

    private fun tools.samt.semantic.ServiceType.OnewayOperation.toPublicOnewayOperation() = object : OnewayOperation {
        override val name = this@toPublicOnewayOperation.name
        override val parameters = this@toPublicOnewayOperation.parameters.map { it.toPublicParameter() }
    }

    private fun tools.samt.semantic.ServiceType.RequestResponseOperation.toPublicRequestResponseOperation() =
        object : RequestResponseOperation {
            override val name = this@toPublicRequestResponseOperation.name
            override val parameters = this@toPublicRequestResponseOperation.parameters.map { it.toPublicParameter() }
            override val returnType = this@toPublicRequestResponseOperation.returnType?.toPublicTypeReference()
            override val raisesTypes =
                this@toPublicRequestResponseOperation.raisesTypes.map { it.toPublicTypeReference() }
            override val isAsync = this@toPublicRequestResponseOperation.isAsync
        }

    private fun tools.samt.semantic.ServiceType.Operation.Parameter.toPublicParameter() =
        object : ServiceOperationParameter {
            override val name = this@toPublicParameter.name
            override val type = this@toPublicParameter.type.toPublicTypeReference()
        }

    private fun tools.samt.semantic.ProviderType.toPublicProvider() = object : ProviderType {
        override val name = this@toPublicProvider.name
        override val qualifiedName = this@toPublicProvider.getQualifiedName()
        override val implements = this@toPublicProvider.implements.map { it.toPublicImplements() }
        override val transport = this@toPublicProvider.transport.toPublicTransport(this)
    }

    private class Params(
        override val config: ConfigurationObject,
        val controller: DiagnosticController
    ) : TransportConfigurationParserParams {

        // TODO use context if provided
        override fun reportError(message: String, context: ConfigurationElement?) {
            controller.reportGlobalError(message)
        }

        override fun reportWarning(message: String, context: ConfigurationElement?) {
            controller.reportGlobalWarning(message)
        }

        override fun reportInfo(message: String, context: ConfigurationElement?) {
            controller.reportGlobalInfo(message)
        }
    }

    private fun tools.samt.semantic.ProviderType.Transport.toPublicTransport(provider: ProviderType): TransportConfiguration {
        val transportConfigurationParsers = transportParsers.filter { it.transportName == name }
        when (transportConfigurationParsers.size) {
            0 -> controller.reportGlobalWarning("No transport configuration parser found for transport '$name'")
            1 -> {
                val transportConfigurationParser = transportConfigurationParsers.single()
                if (configuration != null) {
                    val transportConfigNode = TransportConfigurationMapper(provider, controller).parse(configuration!!)
                    val config = Params(transportConfigNode, controller)
                    try {
                        return transportConfigurationParser.parse(config)
                    } catch (e: Exception) {
                        controller.reportGlobalError("Failed to parse transport configuration for transport '$name': ${e.message}")
                    }
                } else {
                    return transportConfigurationParser.default()
                }
            }

            else -> controller.reportGlobalError("Multiple transport configuration parsers found for transport '$name'")
        }

        return object : TransportConfiguration {}
    }

    private fun tools.samt.semantic.ProviderType.Implements.toPublicImplements() = object : ProviderImplements {
        override val service = this@toPublicImplements.service.toPublicTypeReference().type as ServiceType
        override val operations = this@toPublicImplements.operations.map { it.toPublicOperation() }
    }

    private fun tools.samt.semantic.ConsumerType.toPublicConsumer() = object : ConsumerType {
        override val provider = this@toPublicConsumer.provider.toPublicTypeReference().type as ProviderType
        override val uses = this@toPublicConsumer.uses.map { it.toPublicUses() }
        override val targetPackage = this@toPublicConsumer.parentPackage.nameComponents.joinToString(".")
    }

    private fun tools.samt.semantic.ConsumerType.Uses.toPublicUses() = object : ConsumerUses {
        override val service = this@toPublicUses.service.toPublicTypeReference().type as ServiceType
        override val operations = this@toPublicUses.operations.map { it.toPublicOperation() }
    }

    private fun tools.samt.semantic.AliasType.toPublicAlias() = object : AliasType {
        override val name = this@toPublicAlias.name
        override val qualifiedName = this@toPublicAlias.getQualifiedName()
        override val aliasedType = this@toPublicAlias.aliasedType.toPublicTypeReference()
        override val fullyResolvedType = this@toPublicAlias.fullyResolvedType.toPublicTypeReference()
    }

    private inline fun <reified T : tools.samt.semantic.ResolvedTypeReference.Constraint> List<tools.samt.semantic.ResolvedTypeReference.Constraint>.findConstraint() =
        firstOrNull { it is T } as T?

    private fun tools.samt.semantic.TypeReference?.toPublicTypeReference(): TypeReference {
        check(this is tools.samt.semantic.ResolvedTypeReference)
        val typeReference = this@toPublicTypeReference
        val runtimeTypeReference = when (val type = typeReference.type) {
            is tools.samt.semantic.AliasType -> checkNotNull(type.fullyResolvedType) { "Found unresolved alias when generating code" }
            else -> typeReference
        }
        return object : TypeReference {
            override val type = typeReference.type.toPublicType()
            override val isOptional = typeReference.isOptional
            override val rangeConstraint =
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Range>()
                    ?.toPublicRangeConstraint()
            override val sizeConstraint =
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Size>()
                    ?.toPublicSizeConstraint()
            override val patternConstraint =
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern>()
                    ?.toPublicPatternConstraint()
            override val valueConstraint =
                typeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Value>()
                    ?.toPublicValueConstraint()

            override val runtimeType = runtimeTypeReference.type.toPublicType()
            override val isRuntimeOptional = isOptional || runtimeTypeReference.isOptional
            override val runtimeRangeConstraint = rangeConstraint
                ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Range>()
                    ?.toPublicRangeConstraint()
            override val runtimeSizeConstraint = sizeConstraint
                ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Size>()
                    ?.toPublicSizeConstraint()
            override val runtimePatternConstraint = patternConstraint
                ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern>()
                    ?.toPublicPatternConstraint()
            override val runtimeValueConstraint = valueConstraint
                ?: runtimeTypeReference.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Value>()
                    ?.toPublicValueConstraint()
        }
    }

    private fun tools.samt.semantic.Type.toPublicType() = when (this) {
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
            override val elementType = this@toPublicType.elementType.toPublicTypeReference()
        }

        is tools.samt.semantic.MapType -> object : MapType {
            override val keyType = this@toPublicType.keyType.toPublicTypeReference()
            override val valueType = this@toPublicType.valueType.toPublicTypeReference()
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

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Range.toPublicRangeConstraint() =
        object : Constraint.Range {
            override val lowerBound = this@toPublicRangeConstraint.lowerBound
            override val upperBound = this@toPublicRangeConstraint.upperBound
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Size.toPublicSizeConstraint() =
        object : Constraint.Size {
            override val lowerBound = this@toPublicSizeConstraint.lowerBound
            override val upperBound = this@toPublicSizeConstraint.upperBound
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern.toPublicPatternConstraint() =
        object : Constraint.Pattern {
            override val pattern = this@toPublicPatternConstraint.pattern
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Value.toPublicValueConstraint() =
        object : Constraint.Value {
            override val value = this@toPublicValueConstraint.value
        }

    private fun tools.samt.semantic.UserDeclaredNamedType.getQualifiedName(): String {
        val components = parentPackage.nameComponents + name
        return components.joinToString(".")
    }
}
