package tools.samt.ls

import com.beust.jcommander.Parameter

class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false

    @Parameter(names = ["--server-port", "-sp"])
    var serverPort: Int? = null
    @Parameter(names = ["--client-port", "-cp"])
    var clientPort: Int? = null
    @Parameter(names = ["--client-host", "-ch"])
    var clientHost: String = "localhost"

    @Parameter(names = ["--stdio"])
    var isStdio: Boolean = false
}
