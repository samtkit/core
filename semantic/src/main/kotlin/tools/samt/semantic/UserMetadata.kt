package tools.samt.semantic

class UserMetadata(private val descriptions: Map<Annotated, String>, private val deprecations: Map<Annotated, Deprecation>) {
    data class Deprecation(val message: String?)

    fun getDescription(element: Annotated): String? = descriptions[element]

    fun getDeprecation(element: Annotated): Deprecation? = deprecations[element]
}
