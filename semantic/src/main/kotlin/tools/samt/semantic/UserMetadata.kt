package tools.samt.semantic

class UserMetadata(private val descriptions: Map<UserDeclared, String>, private val deprecatedTypes: Set<UserDeclared>) {
    fun getDescription(element: UserDeclared): String? = descriptions[element]

    fun isDeprecated(element: UserDeclared): Boolean = element in deprecatedTypes
}
