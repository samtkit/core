package tools.samt.semantic

class UserMetadata(private val descriptions: Map<UserDeclared, String>, private val deprecations: Map<UserDeclared, Deprecation>) {
    data class Deprecation(val message: String?)

    fun getDescription(element: UserDeclared): String? = descriptions[element]

    fun getDeprecation(element: UserDeclared): Deprecation? = deprecations[element]
}
