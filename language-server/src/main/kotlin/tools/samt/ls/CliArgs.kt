package tools.samt.ls

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

@Parameters(separators = "=")
class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false

    @Parameter(names = ["--client-host"])
    var clientHost = "localhost"

    /**
     * Option is called Socket because that's what VS Code passes
     */
    @Parameter(names = ["--socket", "--client-port"])
    var clientPort: Int? = null

    @Parameter(names = ["--stdio"])
    var isStdio: Boolean = false
}
