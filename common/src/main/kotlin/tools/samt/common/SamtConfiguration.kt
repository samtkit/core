package tools.samt.common

import java.nio.file.Path

data class SamtConfiguration(
    val source: Path,
    val plugins: List<SamtPluginConfiguration>,
    val generators: List<SamtGeneratorConfiguration>,
)

sealed interface SamtPluginConfiguration

data class SamtLocalPluginConfiguration(
    val path: Path,
) : SamtPluginConfiguration

data class SamtMavenPluginConfiguration(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String,
) : SamtPluginConfiguration

data class SamtGeneratorConfiguration(
    val name: String,
    val output: Path,
    val options: Map<String, String>,
)
