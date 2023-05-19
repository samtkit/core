package tools.samt.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SamtConfiguration(
    val source: String = "./src",
    val repositories: SamtRepositoriesConfiguration = SamtRepositoriesConfiguration(),
    val plugins: List<SamtPluginConfiguration> = emptyList(),
    val generators: List<SamtGeneratorConfiguration> = emptyList(),
)

@Serializable
data class SamtRepositoriesConfiguration(
    val maven: String = "https://repo.maven.apache.org/maven2"
)

@Serializable
sealed interface SamtPluginConfiguration

@Serializable
@SerialName("local")
data class SamtLocalPluginConfiguration(
    val path: String,
) : SamtPluginConfiguration

@Serializable
@SerialName("maven")
data class SamtMavenPluginConfiguration(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repository: String? = null,
) : SamtPluginConfiguration

@Serializable
@SerialName("gradle")
data class SamtGradlePluginConfiguration(
    val dependency: String,
    val repository: String? = null,
) : SamtPluginConfiguration

@Serializable
data class SamtGeneratorConfiguration(
    val name: String,
    val output: String = "./out",
    val options: Map<String, String> = emptyMap(),
)
