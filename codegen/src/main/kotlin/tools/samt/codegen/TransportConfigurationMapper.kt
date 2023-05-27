package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.parser.reportError

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
            highlight("related configuration", location)
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

    private fun tools.samt.parser.IntegerNode.toConfigurationValue() = object : ConfigurationValue {
        override val asString: String get() = reportAndThrow("Unexpected integer, expected a string")

        override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
            reportAndThrow("Unexpected integer, expected an enum (${enum.simpleName})")

        override val asLong: Long get() = value
        override val asDouble: Double = value.toDouble()
        override val asBoolean: Boolean get() = reportAndThrow("Unexpected integer, expected a boolean")
        override val asServiceName: ServiceType get() = reportAndThrow("Unexpected integer, expected a service name")
        override fun asOperationName(service: ServiceType): ServiceOperation =
            reportAndThrow("Unexpected integer, expected an operation name")

        override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected integer, expected an object")
        override val asValue: ConfigurationValue get() = this
        override val asList: ConfigurationList get() = reportAndThrow("Unexpected integer, expected a list")
    }

    private fun tools.samt.parser.FloatNode.toConfigurationValue() = object : ConfigurationValue {
        override val asString: String get() = reportAndThrow("Unexpected float, expected a string")

        override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
            reportAndThrow("Unexpected float, expected an enum (${enum.simpleName})")

        override val asLong: Long get() = reportAndThrow("Unexpected float, expected an integer")
        override val asDouble: Double = value
        override val asBoolean: Boolean get() = reportAndThrow("Unexpected float, expected a boolean")
        override val asServiceName: ServiceType get() = reportAndThrow("Unexpected float, expected a service name")
        override fun asOperationName(service: ServiceType): ServiceOperation =
            reportAndThrow("Unexpected float, expected an operation name")

        override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected float, expected an object")
        override val asValue: ConfigurationValue get() = this
        override val asList: ConfigurationList get() = reportAndThrow("Unexpected float, expected a list")
    }

    private fun tools.samt.parser.StringNode.toConfigurationValue() = object : ConfigurationValue {
        override val asString: String get() = value

        override fun <T : Enum<T>> asEnum(enum: Class<T>): T {
            check(enum.isEnum)
            return enum.enumConstants.find { it.name.equals(value, ignoreCase = true) }
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

    private fun tools.samt.parser.BooleanNode.toConfigurationValue() = object : ConfigurationValue {
        override val asString: String get() = reportAndThrow("Unexpected boolean, expected a string")

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

    private fun tools.samt.parser.IdentifierNode.toConfigurationValue() = object : ConfigurationValue {
        override val asString: String get() = reportAndThrow("Unexpected identifier, expected a string")

        override fun <T : Enum<T>> asEnum(enum: Class<T>): T =
            reportAndThrow("Unexpected identifier, expected an enum (${enum.simpleName})")

        override val asLong: Long get() = reportAndThrow("Unexpected identifier, expected an integer")
        override val asDouble: Double get() = reportAndThrow("Unexpected identifier, expected a float")
        override val asBoolean: Boolean get() = reportAndThrow("Unexpected identifier, expected a boolean")
        override val asServiceName: ServiceType
            get() = provider.implements.find { it.service.name == name }?.service
                ?: reportAndThrow("No service with name '$name' found in provider '${provider.name}'")

        override fun asOperationName(service: ServiceType): ServiceOperation =
            provider.implements.find { it.service.qualifiedName == service.qualifiedName }?.operations?.find { it.name == name }
                ?: reportAndThrow("No operation with name '$name' found in service '${service.name}' of provider '${provider.name}'")

        override val asObject: ConfigurationObject get() = reportAndThrow("Unexpected identifier, expected an object")
        override val asValue: ConfigurationValue get() = this
        override val asList: ConfigurationList get() = reportAndThrow("Unexpected identifier, expected a list")
    }

    private fun tools.samt.parser.ArrayNode.toConfigurationList() = object : ConfigurationList {
        override val entries: List<ConfigurationElement>
            get() = this@toConfigurationList.values.map { it.toConfigurationElement() }
        override val asObject: ConfigurationObject
            get() = reportAndThrow("Unexpected array, expected an object")
        override val asValue: ConfigurationValue
            get() = reportAndThrow("Unexpected array, expected a value")
        override val asList: ConfigurationList
            get() = this
    }

    private fun tools.samt.parser.ObjectNode.toConfigurationObject() = object : ConfigurationObject {
        override val fields: Map<ConfigurationValue, ConfigurationElement>
            get() = this@toConfigurationObject.fields.associate { it.name.toConfigurationValue() to it.value.toConfigurationElement() }

        override fun getField(name: String): ConfigurationElement =
            getFieldOrNull(name) ?: run {
                this@toConfigurationObject.reportError(controller) {
                    message("No field with name '$name' found")
                    highlight("related object", this@toConfigurationObject.location)
                }
                throw NoSuchElementException("No field with name '$name' found")
            }

        override fun getFieldOrNull(name: String): ConfigurationElement? =
            this@toConfigurationObject.fields.find { it.name.name == name }?.value?.toConfigurationElement()

        override val asObject: ConfigurationObject
            get() = this
        override val asValue: ConfigurationValue
            get() {
                this@toConfigurationObject.reportError(controller) {
                    message("Object is not a value")
                    highlight("unexpected object, expected value", this@toConfigurationObject.location)
                }
                error("Object is not a value")
            }
        override val asList: ConfigurationList
            get() {
                this@toConfigurationObject.reportError(controller) {
                    message("Object is not a list")
                    highlight("unexpected object, expected list", this@toConfigurationObject.location)
                }
                error("Object is not a list")
            }
    }
}
