package tools.samt.ls

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

val SAMT_CONFIG_FILE_NAME = Path("samt.yaml")

fun findSamtConfigs(path: Path): List<Path> {
    fun Path.parents(): Sequence<Path> = generateSequence(parent) { it.parent }

    return path.toFile().walkTopDown()
        .map { it.toPath() }
        .filter { it.fileName == SAMT_CONFIG_FILE_NAME && it.isRegularFile() }
        .ifEmpty {
            path.parents()
                .map { it.resolve(SAMT_CONFIG_FILE_NAME) }
                .filter { it.isRegularFile() }
                .take(1)
        }.toList()
}