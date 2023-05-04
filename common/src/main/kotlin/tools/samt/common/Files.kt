package tools.samt.common

import java.io.File

fun List<File>.readSamtSource(controller: DiagnosticController): List<SourceFile> {
    return buildList {
        for (file in this@readSamtSource) {
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

fun collectSamtFiles(directory: String): List<File> {
    val dir = File(directory)
    return dir.walkTopDown().filter { it.isFile && it.extension == "samt" }.toList()
}