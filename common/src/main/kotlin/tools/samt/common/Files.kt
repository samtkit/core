package tools.samt.common

import java.io.File
import java.net.URI

fun List<File>.readSamtSource(controller: DiagnosticController): List<SourceFile> {
    return buildList {
        for (file in this@readSamtSource) {

            if (!file.exists()) {
                controller.reportGlobalError("File '${file.path}' does not exist")
                continue
            }

            if (!file.canRead()) {
                controller.reportGlobalError("File '${file.path}' cannot be read, bad file permissions?")
                continue
            }

            if (file.extension != "samt") {
                controller.reportGlobalError("File '${file.path}' must end in .samt")
                continue
            }

            add(SourceFile(file.toPath().toUri(), content = file.readText()))
        }
    }
}

fun collectSamtFiles(directory: URI): List<File> {
    val dir = File(directory)
    return dir.walkTopDown().filter { it.isFile && it.extension == "samt" }.toList()
}
