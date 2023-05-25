package tools.samt.ls

import tools.samt.config.SamtConfigurationParser
import java.nio.file.Path
import kotlin.io.path.isRegularFile


internal fun Path.findSamtRoots(): List<Path> {
    fun Path.parents(): Sequence<Path> = generateSequence(this) { it.parent }
    fun getSourceDirectory(yamlPath: Path) = try {
        val config = SamtConfigurationParser.parseConfiguration(yamlPath)
        yamlPath.resolveSibling(config.source).normalize()
    } catch (e: SamtConfigurationParser.ParseException) {
        null
    }

    return toFile().walkTopDown()
        .map { it.toPath() }
        .filter { it.endsWith("samt.yaml") && it.isRegularFile() }
        .mapNotNull { getSourceDirectory(it) }
        .ifEmpty {
            parents()
                .map { it.resolve("samt.yaml") }
                .filter { it.isRegularFile() }
                .mapNotNull { getSourceDirectory(it) }
                .take(1)
        }
        .toList()
}