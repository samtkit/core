package tools.samt.semantic

import tools.samt.parser.BundleIdentifierNode
import tools.samt.parser.IdentifierNode
import tools.samt.parser.Node

class Package(val name: String, private val parent: Package?) {
    val subPackages: MutableList<Package> = mutableListOf()

    val records: MutableList<RecordType> = mutableListOf()

    val enums: MutableList<EnumType> = mutableListOf()
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
        require(!isRootPackage)
        records.add(record)
        types[record.name] = record
        linkType(record.declaration, record)
    }

    operator fun plusAssign(enum: EnumType) {
        require(!isRootPackage)
        enums.add(enum)
        types[enum.name] = enum
        linkType(enum.declaration, enum)
    }

    operator fun plusAssign(service: ServiceType) {
        require(!isRootPackage)
        services.add(service)
        types[service.name] = service
        linkType(service.declaration, service)
    }

    operator fun plusAssign(provider: ProviderType) {
        require(!isRootPackage)
        providers.add(provider)
        types[provider.name] = provider
        linkType(provider.declaration, provider)
    }

    operator fun plusAssign(consumer: ConsumerType) {
        require(!isRootPackage)
        consumers.add(consumer)
        linkType(consumer.declaration, consumer)
    }

    operator fun plusAssign(alias: AliasType) {
        require(!isRootPackage)
        aliases.add(alias)
        types[alias.name] = alias
        linkType(alias.declaration, alias)
    }

    operator fun contains(identifier: IdentifierNode): Boolean =
        types.containsKey(identifier.name)

    val isRootPackage: Boolean
        get() = parent == null

    val allSubPackages: List<Package>
        get() = subPackages + subPackages.flatMap { it.allSubPackages }

    val nameComponents: List<String>
        get() = if (isRootPackage) {
            emptyList() // root package
        } else {
            parent!!.nameComponents + name
        }
}
