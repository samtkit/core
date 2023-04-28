package tools.samt.ls

import com.beust.jcommander.Parameter

class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false
}
