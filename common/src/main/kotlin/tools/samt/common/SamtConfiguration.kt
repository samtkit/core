package tools.samt.common

data class SamtConfiguration(
    val source: String,
    val plugins: List<SamtPluginConfiguration>,
    val generators: List<SamtGeneratorConfiguration>,
)

sealed interface SamtPluginConfiguration

data class SamtLocalPluginConfiguration(
    val path: String,
) : SamtPluginConfiguration

data class SamtMavenPluginConfiguration(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String,
) : SamtPluginConfiguration

data class SamtGeneratorConfiguration(
    val name: String,
    val output: String,
    val options: Map<String, String>,
)
