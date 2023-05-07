package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import java.io.File

internal fun List<String>.readSamtSourceFiles(controller: DiagnosticController): List<SourceFile> =
    map { File(it) }.ifEmpty { collectSamtFiles(controller.workingDirectory) }
        .readSamtSource(controller)

