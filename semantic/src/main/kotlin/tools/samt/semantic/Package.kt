package tools.samt.semantic

import tools.samt.parser.*

class Package(val name: String) {
    val subPackages: MutableList<Package> = mutableListOf()

    val records: MutableList<RecordType> = mutableListOf()

    @Suppress("MemberVisibilityCanBePrivate")
    val enums: MutableList<EnumType> = mutableListOf() // Will be read in the future
    val services: MutableList<ServiceType> = mutableListOf()
    val providers: MutableList<ProviderType> = mutableListOf()
    val consumers: MutableList<ConsumerType> = mutableListOf()
    val aliases: MutableList<AliasType> = mutableListOf()

    val typeByNode: MutableMap<Node, Type> = mutableMapOf()

    val types: MutableMap<String, Type> = mutableMapOf()

    inline fun <reified T> getTypeOrNullByNode(node: Node): T? {
        val type = typeByNode[node]
        check(type is T?) { "Expected type ${T::class.simpleName} for ${node.javaClass.simpleName} at ${node.location} but got ${type!!.javaClass.simpleName}" }
        return type
    }

    inline fun <reified T> getTypeByNode(node: Node): T {
        val type = getTypeOrNullByNode<T>(node)
        checkNotNull(type) { "No type found for node of type ${node.javaClass.simpleName} at ${node.location}" }
        return type
    }

    fun resolveSubPackage(name: BundleIdentifierNode): Package {
        var samtPackage = this
        for (namespace in name.components) {
            samtPackage = samtPackage.subPackages.first { it.name == namespace.name }
        }
        return samtPackage
    }

    fun resolveType(identifier: IdentifierNode): Type? =
        subPackages.find { it.name == identifier.name }?.let { PackageType(it) } ?: types[identifier.name]

    fun linkType(source: Node, type: Type) {
        typeByNode[source] = type
    }

    operator fun plusAssign(record: RecordType) {
        records.add(record)
        types[record.name] = record
        typeByNode[record.declaration] = record
    }

    operator fun plusAssign(enum: EnumType) {
        enums.add(enum)
        types[enum.name] = enum
        typeByNode[enum.declaration] = enum
    }

    operator fun plusAssign(service: ServiceType) {
        services.add(service)
        types[service.name] = service
        typeByNode[service.declaration] = service
    }

    operator fun plusAssign(provider: ProviderType) {
        providers.add(provider)
        types[provider.name] = provider
        typeByNode[provider.declaration] = provider
    }

    operator fun plusAssign(consumer: ConsumerType) {
        consumers.add(consumer)
        typeByNode[consumer.declaration] = consumer
    }

    operator fun plusAssign(alias: AliasType) {
        aliases.add(alias)
        types[alias.name] = alias
        typeByNode[alias.declaration] = alias
    }

    operator fun contains(identifier: IdentifierNode): Boolean =
        types.containsKey(identifier.name)

    val allSubPackages: List<Package>
        get() = subPackages + subPackages.flatMap { it.allSubPackages }
}
