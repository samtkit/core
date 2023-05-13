package tools.samt.codegen

import tools.samt.common.DiagnosticController

class PublicApiMapper(
    private val transportParsers: List<TransportConfigurationParser>,
    private val controller: DiagnosticController,
) {
    fun toPublicApi(samtPackage: tools.samt.semantic.Package) = object : SamtPackage {
        override val name = samtPackage.name
        override val qualifiedName = samtPackage.nameComponents.joinToString(".")
        override val records = samtPackage.records.map { it.toPublicApi() }
        override val enums = samtPackage.enums.map { it.toPublicApi() }
        override val services = samtPackage.services.map { it.toPublicApi() }
        override val providers = samtPackage.providers.map { it.toPublicApi() }
        override val consumers = samtPackage.consumers.map { it.toPublicApi() }
        override val aliases = samtPackage.aliases.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.RecordType.toPublicApi() = object : RecordType {
        override val name = this@toPublicApi.name
        override val qualifiedName = this@toPublicApi.getQualifiedName()
        override val fields = this@toPublicApi.fields.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.RecordType.Field.toPublicApi() = object : RecordField {
        override val name = this@toPublicApi.name
        override val type = this@toPublicApi.type.toPublicApi()
    }

    private fun tools.samt.semantic.EnumType.toPublicApi() = object : EnumType {
        override val name = this@toPublicApi.name
        override val qualifiedName = this@toPublicApi.getQualifiedName()
        override val values = this@toPublicApi.values
    }

    private fun tools.samt.semantic.ServiceType.toPublicApi() = object : ServiceType {
        override val name = this@toPublicApi.name
        override val qualifiedName = this@toPublicApi.getQualifiedName()
        override val operations = this@toPublicApi.operations.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.ServiceType.Operation.toPublicApi() = when (this) {
        is tools.samt.semantic.ServiceType.OnewayOperation -> toPublicApi()
        is tools.samt.semantic.ServiceType.RequestResponseOperation -> toPublicApi()
    }

    private fun tools.samt.semantic.ServiceType.OnewayOperation.toPublicApi() = object : OnewayOperation {
        override val name = this@toPublicApi.name
        override val parameters = this@toPublicApi.parameters.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.ServiceType.RequestResponseOperation.toPublicApi() =
        object : RequestResponseOperation {
            override val name = this@toPublicApi.name
            override val parameters = this@toPublicApi.parameters.map { it.toPublicApi() }
            override val returnType = this@toPublicApi.returnType?.toPublicApi()
            override val raisesTypes = this@toPublicApi.raisesTypes.map { it.toPublicApi() }
            override val isAsync = this@toPublicApi.isAsync
        }

    private fun tools.samt.semantic.ServiceType.Operation.Parameter.toPublicApi() = object : ServiceOperationParameter {
        override val name = this@toPublicApi.name
        override val type = this@toPublicApi.type.toPublicApi()
    }

    private fun tools.samt.semantic.ProviderType.toPublicApi() = object : ProviderType {
        override val name = this@toPublicApi.name
        override val qualifiedName = this@toPublicApi.getQualifiedName()
        override val implements = this@toPublicApi.implements.map { it.toPublicApi() }
        override val transport = this@toPublicApi.transport.toPublicApi()
    }

    private fun tools.samt.semantic.ProviderType.Transport.toPublicApi(): TransportConfiguration {
        val transportConfigNode = configuration
        val transportConfigurationParser = transportParsers.filter { it.transportName == name }
        when (transportConfigurationParser.size) {
            0 -> controller.reportGlobalError("No transport configuration parser found for transport '$name'")
            1 -> {
                return if (transportConfigNode != null) {
                    // TODO transform transportConfigNode to a Map<String, Any>
                    transportConfigurationParser.single().parse(emptyMap())
                } else {
                    transportConfigurationParser.single().default()
                }
            }

            else -> controller.reportGlobalError("Multiple transport configuration parsers found for transport '$name'")
        }
        return object : TransportConfiguration {}
    }

    private fun tools.samt.semantic.ProviderType.Implements.toPublicApi() = object : ProviderImplements {
        override val service = this@toPublicApi.service.toPublicApi()
        override val operations = this@toPublicApi.operations.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.ConsumerType.toPublicApi() = object : ConsumerType {
        override val provider = this@toPublicApi.provider.toPublicApi().type as ProviderType
        override val uses = this@toPublicApi.uses.map { it.toPublicApi() }
        override val targetPackage = this@toPublicApi.parentPackage.nameComponents.joinToString(".")
    }

    private fun tools.samt.semantic.ConsumerType.Uses.toPublicApi() = object : ConsumerUses {
        override val service = this@toPublicApi.service.toPublicApi()
        override val operations = this@toPublicApi.operations.map { it.toPublicApi() }
    }

    private fun tools.samt.semantic.AliasType.toPublicApi() = object : AliasType {
        override val name = this@toPublicApi.name
        override val qualifiedName = this@toPublicApi.getQualifiedName()
        override val aliasedType = this@toPublicApi.aliasedType.toPublicApi()
        override val fullyResolvedType = this@toPublicApi.fullyResolvedType.toPublicApi()
    }

    private inline fun <reified T : tools.samt.semantic.ResolvedTypeReference.Constraint> List<tools.samt.semantic.ResolvedTypeReference.Constraint>.findConstraint() =
        firstOrNull { it is T } as T?

    private fun tools.samt.semantic.TypeReference?.toPublicApi(): TypeReference {
        check(this is tools.samt.semantic.ResolvedTypeReference)
        return object : TypeReference {
            override val type = this@toPublicApi.type.toPublicApi()
            override val isOptional = this@toPublicApi.isOptional
            override val rangeConstraint =
                this@toPublicApi.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Range>()
                    ?.toPublicApi()
            override val sizeConstraint =
                this@toPublicApi.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Size>()
                    ?.toPublicApi()
            override val patternConstraint =
                this@toPublicApi.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern>()
                    ?.toPublicApi()
            override val valueConstraint =
                this@toPublicApi.constraints.findConstraint<tools.samt.semantic.ResolvedTypeReference.Constraint.Value>()
                    ?.toPublicApi()
        }
    }

    private fun tools.samt.semantic.Type.toPublicApi() = when (this) {
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
            override val elementType = this@toPublicApi.elementType.toPublicApi()
        }

        is tools.samt.semantic.MapType -> object : MapType {
            override val keyType = this@toPublicApi.keyType.toPublicApi()
            override val valueType = this@toPublicApi.valueType.toPublicApi()
        }

        is tools.samt.semantic.AliasType -> toPublicApi()
        is tools.samt.semantic.ConsumerType -> toPublicApi()
        is tools.samt.semantic.EnumType -> toPublicApi()
        is tools.samt.semantic.ProviderType -> toPublicApi()
        is tools.samt.semantic.RecordType -> toPublicApi()
        is tools.samt.semantic.ServiceType -> toPublicApi()
        is tools.samt.semantic.PackageType -> error("Package type cannot be converted to public API")
        tools.samt.semantic.UnknownType -> error("Unknown type cannot be converted to public API")
    }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Range.toPublicApi() = object : Constraint.Range {
        override val lowerBound = this@toPublicApi.lowerBound
        override val upperBound = this@toPublicApi.upperBound
    }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Size.toPublicApi() = object : Constraint.Size {
        override val lowerBound = this@toPublicApi.lowerBound
        override val upperBound = this@toPublicApi.upperBound
    }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Pattern.toPublicApi() =
        object : Constraint.Pattern {
            override val pattern = this@toPublicApi.pattern
        }

    private fun tools.samt.semantic.ResolvedTypeReference.Constraint.Value.toPublicApi() = object : Constraint.Value {
        override val value = this@toPublicApi.value
    }

    private fun tools.samt.semantic.UserDeclaredNamedType.getQualifiedName(): String {
        val components = parentPackage.nameComponents + name
        return components.joinToString(".")
    }
}
