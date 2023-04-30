package tools.samt.cli

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tools.samt.common.DiagnosticController
import java.io.File
import java.net.URL

internal fun wrapper(command: WrapperCommand, terminal: Terminal, controller: DiagnosticController) {
    val workingDirectory = controller.workingDirectoryAbsolutePath
    val wrapperDirectory = File(workingDirectory, "wrapper")

    if (command.init) {
        terminal.println("Initializing the SAMT wrapper in this directory...")
        if (!wrapperDirectory.mkdirs()) {
            controller.reportGlobalWarning("The SAMT wrapper has already been initialized, overriding existing files")
        }

        fun downloadFile(file: String) {
            try {
                val fileName = if (command.initSource.endsWith("/")) {
                    "${command.initSource}$file"
                } else {
                    "${command.initSource}/$file"
                }

                URL(fileName).openStream().use { input ->
                    File(wrapperDirectory, file).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                controller.reportGlobalInfo("Downloaded $file from $fileName")
            } catch (e: Exception) {
                controller.reportGlobalError("Failed to download $file from ${command.initSource} (exception: $e)")
            }
        }

        downloadFile("samtw")
        downloadFile("samtw.bat")
        downloadFile("samt-wrapper.properties")

        controller.reportGlobalInfo("The SAMT wrapper has been initialized in this directory")
    }

    if (!wrapperDirectory.exists()) {
        controller.reportGlobalError("The SAMT wrapper has not been initialized in this directory, run './samtw wrapper --init' to initialize it")
        return
    }

    terminal.println("Checking for SAMT wrapper update...")
    val newVersion = when (command.version) {
        "latest" -> {
            try {
                URL(command.latestVersionSource).openStream().use { input ->
                    val response = json.decodeFromString<LatestSamtVersionResponse>(input.reader().readText())
                    response.tagName
                }
            } catch (e: Exception) {
                controller.reportGlobalError("Failed to query the latest version of the SAMT wrapper from ${command.latestVersionSource} (exception: $e)")
                return
            }
        }

        else -> command.version
    }

    val wrapperPropertiesFile = File(wrapperDirectory, "samt-wrapper.properties")
    val wrapperProperties = wrapperPropertiesFile.readText()

    var currentVersion: String? = null
    for (line in wrapperProperties.lineSequence()) {
        if (line.startsWith("samtVersion=")) {
            currentVersion = line.substringAfter("=").trim()
        }
    }

    if (currentVersion == null) {
        controller.reportGlobalError("Failed to parse the current version of the SAMT wrapper from ${wrapperPropertiesFile.absolutePath}")
        return
    }

    if (currentVersion == newVersion) {
        controller.reportGlobalInfo("The SAMT wrapper is already up-to-date")
        return
    }

    wrapperPropertiesFile.writeText(wrapperProperties.replace(currentVersion, newVersion))
    controller.reportGlobalInfo("The SAMT wrapper has been updated from $currentVersion to $newVersion")
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LatestSamtVersionResponse(
    @SerialName("tag_name")
    val tagName: String,
)