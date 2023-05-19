package tools.samt.cli

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

class CliArgs {
    @Parameter(names = ["-h", "--help"], description = "Display help", help = true)
    var help: Boolean = false
}

@Parameters(commandDescription = "Compile SAMT files")
class CompileCommand {
    @Parameter(description = "SAMT project to compile, defaults to the 'samt.yaml' file in the current directory")
    var file: String = "./samt.yaml"
}

@Parameters(commandDescription = "Dump SAMT files in various formats for debugging purposes")
class DumpCommand {
    @Parameter(names = ["--tokens"], description = "Dump a visual representation of the token stream")
    var dumpTokens: Boolean = false

    @Parameter(names = ["--ast"], description = "Dump a visual representation of the AST")
    var dumpAst: Boolean = false

    @Parameter(names = ["--types"], description = "Dump a visual representation of the resolved types")
    var dumpTypes: Boolean = false

    @Parameter(description = "SAMT project to dump, defaults to the 'samt.yaml' file in the current directory")
    var file: String = "./samt.yaml"
}

@Parameters(commandDescription = "Initialize or update the SAMT wrapper")
class WrapperCommand {
    @Parameter(names = ["--version"], description = "The SAMT version to use, defaults to the latest version published on GitHub")
    var version: String = "latest"

    @Parameter(names = ["--version-source"], description = "The location from where the latest version will be fetched from, defaults to the GitHub API. The result must be a JSON object with a 'tag_name' field containing the version string")
    var latestVersionSource: String = "https://api.github.com/repos/samtkit/core/releases/latest"

    @Parameter(names = ["--init"], description = "Downloads all required files and initializes the SAMT wrapper")
    var init: Boolean = false

    @Parameter(names = ["--init-source"], description = "The location from where the initial 'samtw', 'samtw.bat' and 'samt-wrapper.properties' will be downloaded from")
    var initSource: String = "https://raw.githubusercontent.com/samtkit/core/main/wrapper"
}
