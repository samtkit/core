package tools.samt.cli

import com.beust.jcommander.Parameter

class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false

    @Parameter(names = ["--dump-ast"], description = "Dump a visual representation of the AST", help = true)
    var dumpAst: Boolean = false

    @Parameter(description = "Files")
    var files: List<String> = mutableListOf()
}
