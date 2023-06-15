package tools.samt.api.types

import tools.samt.api.plugin.TransportConfiguration

/**
 * A user declared type
 */
interface UserType : Type {
    val name: String
    val qualifiedName: String
}

/**
 * A type alias to another type
 */
interface AliasType : UserType {
    /**
     * The type this alias stands for, could be another alias
     */
    val aliasedType: TypeReference

    /**
     * The runtime type, which will not contain any type aliases, just the underlying merged type
     */
    val runtimeType: TypeReference
}

/**
 * A SAMT record
 */
interface RecordType : UserType {
    val fields: List<RecordField>
}

/**
 * A field in a record
 */
interface RecordField {
    val name: String
    val type: TypeReference
}

/**
 * A SAMT enum
 */
interface EnumType : UserType {
    val values: List<String>
}

/**
 * A SAMT service
 */
interface ServiceType : UserType {
    val operations: List<ServiceOperation>
}

/**
 * An operation in a service
 */
interface ServiceOperation {
    val name: String
    val parameters: List<ServiceOperationParameter>
}

/**
 * A parameter in a service operation
 */
interface ServiceOperationParameter {
    val name: String
    val type: TypeReference
}

/**
 * A service operation that returns a response
 */
interface RequestResponseOperation : ServiceOperation {
    /**
     * The return type of this operation.
     * If null, this operation returns nothing.
     */
    val returnType: TypeReference?

    /**
     * Is true if this operation is asynchronous.
     * This could mean that the operation returns a future in Java, or a Promise in JavaScript.
     */
    val isAsync: Boolean
}

/**
 * A service operation that is fire-and-forget, never returning a response
 */
interface OnewayOperation : ServiceOperation

/**
 * A SAMT provider
 */
interface ProviderType : UserType {
    val implements: List<ProvidedService>
    val transport: TransportConfiguration
}

/**
 * Connects a provider to a service
 */
interface ProvidedService {
    /**
     * The underlying service this provider implements
     */
    val service: ServiceType

    /**
     * The operations that are implemented by this provider
     */
    val implementedOperations: List<ServiceOperation>

    /**
     * The operations that are not implemented by this provider
     */
    val unimplementedOperations: List<ServiceOperation>
}

/**
 * A SAMT consumer
 */
interface ConsumerType : Type {
    /**
     * The provider this consumer is connected to
     */
    val provider: ProviderType

    /**
     * The services this consumer uses
     */
    val uses: List<ConsumedService>

    /**
     * The package this consumer is located in
     */
    val samtPackage: String
}

/**
 * Connects a consumer to a service
 */
interface ConsumedService {
    /**
     * The underlying service this consumer uses
     */
    val service: ServiceType

    /**
     * The operations that are consumed by this consumer
     */
    val consumedOperations: List<ServiceOperation>

    /**
     * The operations that are not consumed by this consumer
     */
    val unconsumedOperations: List<ServiceOperation>
}
