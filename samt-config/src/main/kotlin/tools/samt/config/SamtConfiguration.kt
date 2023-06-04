package tools.samt.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SamtConfiguration(
    val source: String = "./src",
    val repositories: SamtRepositoriesConfiguration = SamtRepositoriesConfiguration(),
    val plugins: List<SamtPluginConfiguration> = emptyList(),
    val generators: List<SamtGeneratorConfiguration> = emptyList(),
)

@Serializable
internal data class SamtRepositoriesConfiguration(
    val maven: String = "https://repo.maven.apache.org/maven2",
)

@Serializable
internal sealed interface SamtPluginConfiguration

@Serializable
@SerialName("local")
internal data class SamtLocalPluginConfiguration(
    val path: String,
) : SamtPluginConfiguration

@Serializable
@SerialName("maven")
internal data class SamtMavenPluginConfiguration(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String? = null,
) : SamtPluginConfiguration

@Serializable
@SerialName("gradle")
internal data class SamtGradlePluginConfiguration(
    val dependency: String,
    val repository: String? = null,
) : SamtPluginConfiguration

@Serializable
internal data class SamtGeneratorConfiguration(
    val name: String,
    val output: String = "./out",
    val options: Map<String, String> = emptyMap(),
)
