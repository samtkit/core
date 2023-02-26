rootProject.name = "samt-core"
include("cli")

dependencyResolutionManagement {
    versionCatalogs {
        val shadow = "8.1.0"
        val jCommander = "1.82"

        create("libs") {
            library("jCommander", "com.beust", "jcommander").version(jCommander)

            plugin("shadow", "com.github.johnrengelman.shadow").version(shadow)
        }
    }
}
