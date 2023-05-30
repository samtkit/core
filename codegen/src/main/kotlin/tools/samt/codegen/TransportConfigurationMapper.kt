package tools.samt.codegen

import tools.samt.api.plugin.ConfigurationElement
import tools.samt.api.plugin.ConfigurationList
import tools.samt.api.plugin.ConfigurationObject
import tools.samt.api.plugin.ConfigurationValue
import tools.samt.api.types.ProviderType
import tools.samt.api.types.ServiceOperation
import tools.samt.api.types.ServiceType
import tools.samt.common.DiagnosticController
import tools.samt.parser.reportError

interface PublicApiConfigurationMapping {
    val original: tools.samt.parser.Node
}

class TransportConfigurationMapper(
    private val provider: ProviderType,
    private val controller: DiagnosticController,
) {
    fun parse(configuration: tools.samt.parser.ObjectNode): ConfigurationObject {
        return configuration.toConfigurationObject()
    }

    private fun tools.samt.parser.Node.reportAndThrow(message: String): Nothing {
        reportError(controller) {
            message(message)
            highlight("offending configuration", location)
        }
        error(message)
    }

    private fun tools.samt.parser.ExpressionNode.toConfigurationElement(): ConfigurationElement = when (this) {
        is tools.samt.parser.ArrayNode -> toConfigurationList()
        is tools.samt.parser.BooleanNode -> toConfigurationValue()
        is tools.samt.parser.BundleIdentifierNode -> components.last().toConfigurationValue()
        is tools.samt.parser.IdentifierNode -> toConfigurationValue()
        is tools.samt.parser.FloatNode -> toConfigurationValue()
        is tools.samt.parser.IntegerNode -> toConfigurationValue()
        is tools.samt.parser.ObjectNode -> toConfigurationObject()
        is tools.samt.parser.StringNode -> toConfigurationValue()
        else -> reportAndThrow("Unexpected expression")
    }

    private fun tools.samt.parser.IntegerNode.toConfigurationValue() =
        object : ConfigurationValue, PublicApiConfigurationMapping {
            override val original = this@toConfigurationValue
            override val asString: String get() = reportAndThrow("Unexpected integer, expected a string")
            override val asIdentifier: String get() = reportAndThrow("Unexpected integer, expected an identifier")

            override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
                reportAndThrow("Unexpected integer, expected an enum (${enum.simpleName})")

            override val asLong: Long get() = original.value
            override val asDouble: Double = original.value.toDouble()
            override val asBoolean: Boolean get() = reportAndThrow("Unexpected integer, expected a boolean")
            override val asServiceName: ServiceType get() = reportAndThrow("Unexpected integer, expected a service name")
            override fun asOperationName(service: ServiceType): ServiceOperation =
                reportAndThrow("Unexpected integer, expected an operation name")

            override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected integer, expected an object")
            override val asValue: ConfigurationValue get() = this
            override val asList: ConfigurationList get() = reportAndThrow("Unexpected integer, expected a list")
        }

    private fun tools.samt.parser.FloatNode.toConfigurationValue() =
        object : ConfigurationValue, PublicApiConfigurationMapping {
            override val original = this@toConfigurationValue
            override val asString: String get() = reportAndThrow("Unexpected float, expected a string")
            override val asIdentifier: String get() = reportAndThrow("Unexpected float, expected an identifier")

            override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
                reportAndThrow("Unexpected float, expected an enum (${enum.simpleName})")

            override val asLong: Long get() = reportAndThrow("Unexpected float, expected an integer")
            override val asDouble: Double = original.value
            override val asBoolean: Boolean get() = reportAndThrow("Unexpected float, expected a boolean")
            override val asServiceName: ServiceType get() = reportAndThrow("Unexpected float, expected a service name")
            override fun asOperationName(service: ServiceType): ServiceOperation =
                reportAndThrow("Unexpected float, expected an operation name")

            override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected float, expected an object")
            override val asValue: ConfigurationValue get() = this
            override val asList: ConfigurationList get() = reportAndThrow("Unexpected float, expected a list")
        }

    private fun tools.samt.parser.StringNode.toConfigurationValue() =
        object : ConfigurationValue, PublicApiConfigurationMapping {
            override val original = this@toConfigurationValue
            override val asString: String get() = original.value
            override val asIdentifier: String get() = reportAndThrow("Unexpected string, expected an identifier")

            override fun <T : Enum<T>> asEnum(enum: Class<T>): T {
                check(enum.isEnum)
                return enum.enumConstants.find { it.name.equals(original.value, ignoreCase = true) }
                    ?: reportAndThrow("Illegal enum value, expected one of ${enum.enumConstants.joinToString { it.name }}")
            }

            override val asLong: Long get() = reportAndThrow("Unexpected string, expected an integer")
            override val asDouble: Double get() = reportAndThrow("Unexpected string, expected a float")
            override val asBoolean: Boolean get() = reportAndThrow("Unexpected string, expected a boolean")
            override val asServiceName: ServiceType get() = reportAndThrow("Unexpected string, expected a service name")
            override fun asOperationName(service: ServiceType): ServiceOperation =
                reportAndThrow("Unexpected string, expected an operation name")

            override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected string, expected an object")
            override val asValue: ConfigurationValue get() = this
            override val asList: ConfigurationList get() = reportAndThrow("Unexpected string, expected a list")
        }

    private fun tools.samt.parser.BooleanNode.toConfigurationValue() =
        object : ConfigurationValue, PublicApiConfigurationMapping {
            override val original = this@toConfigurationValue
            override val asString: String get() = reportAndThrow("Unexpected boolean, expected a string")
            override val asIdentifier: String get() = reportAndThrow("Unexpected boolean, expected an identifier")

            override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
                reportAndThrow("Unexpected boolean, expected an enum (${enum.simpleName})")

            override val asLong: Long get() = reportAndThrow("Unexpected boolean, expected an integer")
            override val asDouble: Double get() = reportAndThrow("Unexpected boolean, expected a float")
            override val asBoolean: Boolean get() = value
            override val asServiceName: ServiceType get() = reportAndThrow("Unexpected boolean, expected a service name")
            override fun asOperationName(service: ServiceType): ServiceOperation =
                reportAndThrow("Unexpected boolean, expected an operation name")

            override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected boolean, expected an object")
            override val asValue: ConfigurationValue get() = this
            override val asList: ConfigurationList get() = reportAndThrow("Unexpected boolean, expected a list")
        }

    private fun tools.samt.parser.IdentifierNode.toConfigurationValue() =
        object : ConfigurationValue, PublicApiConfigurationMapping {
            override val original = this@toConfigurationValue
            override val asString: String get() = reportAndThrow("Unexpected identifier, expected a string")
            override val asIdentifier: String get() = original.name

            override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
                reportAndThrow("Unexpected identifier, expected an enum (${enum.simpleName})")

            override val asLong: Long get() = reportAndThrow("Unexpected identifier, expected an integer")
            override val asDouble: Double get() = reportAndThrow("Unexpected identifier, expected a float")
            override val asBoolean: Boolean get() = reportAndThrow("Unexpected identifier, expected a boolean")
            override val asServiceName: ServiceType
                get() = provider.implements.find { it.service.name == original.name }?.service
                    ?: reportAndThrow("No service with name '${original.name}' found in provider '${provider.name}'")

            override fun asOperationName(service: ServiceType): ServiceOperation =
                provider.implements.find { it.service.qualifiedName == service.qualifiedName }?.implementedOperations?.find { it.name == original.name }
                    ?: reportAndThrow("No operation with name '${original.name}' found in service '${service.name}' of provider '${provider.name}'")

            override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected identifier, expected an object")
            override val asValue: ConfigurationValue get() = this
            override val asList: ConfigurationList get() = reportAndThrow("Unexpected identifier, expected a list")
        }

    private fun tools.samt.parser.ArrayNode.toConfigurationList() =
        object : ConfigurationList, PublicApiConfigurationMapping {
            override val original = this@toConfigurationList
            override val entries: List<ConfigurationElement>
                get() = original.values.map { it.toConfigurationElement() }
            override val asObject: ConfigurationObject
                get() = reportAndThrow("Unexpected array, expected an object")
            override val asValue: ConfigurationValue
                get() = reportAndThrow("Unexpected array, expected a value")
            override val asList: ConfigurationList
                get() = this
        }

    private fun tools.samt.parser.ObjectNode.toConfigurationObject() =
        object : ConfigurationObject, PublicApiConfigurationMapping {
            override val original = this@toConfigurationObject
            override val fields: Map<ConfigurationValue, ConfigurationElement>
                get() = original.fields.associate { it.name.toConfigurationValue() to it.value.toConfigurationElement() }

            override fun getField(name: String): ConfigurationElement =
                getFieldOrNull(name) ?: run {
                    original.reportError(controller) {
                        message("No field with name '$name' found")
                        highlight("related object", original.location)
                    }
                    throw NoSuchElementException("No field with name '$name' found")
                }

            override fun getFieldOrNull(name: String): ConfigurationElement? =
                original.fields.find { it.name.name == name }?.value?.toConfigurationElement()

            override val asObject: ConfigurationObject
                get() = this
            override val asValue: ConfigurationValue
                get() {
                    original.reportError(controller) {
                        message("Object is not a value")
                        highlight("unexpected object, expected value", original.location)
                    }
                    error("Object is not a value")
                }
            override val asList: ConfigurationList
                get() {
                    original.reportError(controller) {
                        message("Object is not a list")
                        highlight("unexpected object, expected list", original.location)
                    }
                    error("Object is not a list")
                }
        }
}
