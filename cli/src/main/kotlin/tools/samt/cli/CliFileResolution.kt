package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import java.io.File

internal fun List<String>.readSamtSourceFiles(controller: DiagnosticController): List<SourceFile> {
    val files = map { File(it) }.ifEmpty { gatherSamtFiles(controller.workingDirectoryAbsolutePath) }

    return buildList {
        for (file in files) {
            if (!file.exists()) {
                controller.reportGlobalError("File '${file.canonicalPath}' does not exist")
                continue
            }

            if (!file.canRead()) {
                controller.reportGlobalError("File '${file.canonicalPath}' cannot be read, bad file permissions?")
                continue
            }

            if (file.extension != "samt") {
                controller.reportGlobalError("File '${file.canonicalPath}' must end in .samt")
                continue
            }

            add(SourceFile(file.canonicalPath, content = file.readText()))
        }
    }
}

internal fun gatherSamtFiles(directory: String): List<File> {
    val dir = File(directory)
    return dir.walkTopDown().filter { it.isFile && it.extension == "samt" }.toList()
}
