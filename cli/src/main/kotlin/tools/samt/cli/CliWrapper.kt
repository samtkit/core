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
    val workingDirectory = File(controller.workingDirectory)
    val dotSamtDirectory = File(workingDirectory, ".samt")

    if (command.init) {
        terminal.println("Initializing the SAMT wrapper in this directory...")
        dotSamtDirectory.mkdirs()

        fun downloadFile(file: String, targetDirectory: File, executable: Boolean) {
            try {
                val fileName = if (command.initSource.endsWith("/")) {
                    "${command.initSource}$file"
                } else {
                    "${command.initSource}/$file"
                }

                val targetFile = File(targetDirectory, file)
                val fileExisted = targetFile.exists()

                URL(fileName).openStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (executable) {
                    targetFile.setExecutable(true)
                }

                if (fileExisted) {
                    controller.reportGlobalWarning("Downloaded $file from $fileName, overwriting existing file")
                } else {
                    controller.reportGlobalInfo("Downloaded $file from $fileName")
                }
            } catch (e: Exception) {
                controller.reportGlobalError("Failed to download $file from ${command.initSource} (exception: $e)")
            }
        }

        downloadFile("samtw", workingDirectory, executable = true)
        downloadFile("samtw.bat", workingDirectory, executable = true)
        downloadFile("samt-wrapper.properties", dotSamtDirectory, executable = false)

        controller.reportGlobalInfo("The SAMT wrapper has been initialized in this directory")
    }

    val wrapperPropertiesFile = File(dotSamtDirectory, "samt-wrapper.properties")

    if (!wrapperPropertiesFile.exists()) {
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
