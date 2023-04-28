package tools.samt.semantic

import tools.samt.parser.IdentifierNode
import tools.samt.parser.NamedDeclarationNode

class Package(val name: String) {
    val subPackages: MutableList<Package> = mutableListOf()

    val records: MutableList<RecordType> = mutableListOf()

    @Suppress("MemberVisibilityCanBePrivate")
    val enums: MutableList<EnumType> = mutableListOf() // Will be read in the future
    val services: MutableList<ServiceType> = mutableListOf()
    val providers: MutableList<ProviderType> = mutableListOf()
    val consumers: MutableList<ConsumerType> = mutableListOf()

    val types: MutableMap<String, Type> = mutableMapOf()

    fun resolveType(identifier: IdentifierNode): Type? =
        subPackages.find { it.name == identifier.name }?.let { PackageType(it) } ?: types[identifier.name]

    operator fun plusAssign(record: RecordType) {
        records.add(record)
        types[record.name] = record
    }

    operator fun plusAssign(enum: EnumType) {
        enums.add(enum)
        types[enum.name] = enum
    }

    operator fun plusAssign(service: ServiceType) {
        services.add(service)
        types[service.name] = service
    }

    operator fun plusAssign(provider: ProviderType) {
        providers.add(provider)
        types[provider.name] = provider
    }

    operator fun plusAssign(consumer: ConsumerType) {
        consumers.add(consumer)
    }

    operator fun contains(declaration: NamedDeclarationNode): Boolean =
        types.containsKey(declaration.name.name)

    operator fun contains(identifier: IdentifierNode): Boolean =
        types.containsKey(identifier.name)

    operator fun contains(name: String): Boolean =
        types.containsKey(name)

    val allSubPackages: List<Package>
        get() = subPackages + subPackages.flatMap { it.allSubPackages }
}
