package tools.samt.cli

import com.beust.jcommander.Parameter

class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false

    @Parameter(names = ["--benchmark"], description = "Parse files multiple times and measure execution time", help = true)
    var benchmark: Boolean = false

    @Parameter(names = ["--benchmark-runs"], description = "Parse files multiple times and measure execution time", help = true)
    var benchmarkRuns: Int = 100

    @Parameter(names = ["--dumpast"], description = "Dump a visual representation of the AST", help = true)
    var dumpAst: Boolean = false

    @Parameter(description = "Files")
    var files: List<String> = mutableListOf()
}
