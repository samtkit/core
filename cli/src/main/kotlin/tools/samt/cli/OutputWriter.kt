package tools.samt.cli

import tools.samt.api.plugin.CodegenFile
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.*

internal object OutputWriter {
    @Throws(IOException::class)
    fun write(outputDirectory: Path, files: List<CodegenFile>) {
        if (!outputDirectory.exists()) {
            try {
                outputDirectory.createDirectories()
            } catch (e: IOException) {
                throw IOException("Failed to create output directory '${outputDirectory}'", e)
            }
        }
        if (!outputDirectory.isDirectory()) {
            throw IOException("Path '${outputDirectory}' does not point to a directory")
        }
        for (file in files) {
            val outputFile = try {
                outputDirectory.resolve(file.filepath)
            } catch (e: InvalidPathException) {
                throw IOException("Invalid path '${file.filepath}'", e)
            }
            try {
                outputFile.parent.createDirectories()
                if (outputFile.notExists()) {
                    outputFile.createFile()
                }
                outputFile.writeText(file.source)
            } catch (e: IOException) {
                throw IOException("Failed to write file '${outputFile.toUri()}'", e)
            }
        }
    }
}
