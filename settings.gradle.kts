rootProject.name = "samt-core"
include(":common")
include(":cli")
include(":lexer")
include(":parser")

dependencyResolutionManagement {
    versionCatalogs {
        val shadow = "8.1.0"
        val jCommander = "1.82"
        val mordant = "2.0.0-beta12"
        val kover = "0.6.1"

        create("libs") {
            library("jCommander", "com.beust", "jcommander").version(jCommander)
            library("mordant", "com.github.ajalt.mordant", "mordant").version(mordant)

            plugin("shadow", "com.github.johnrengelman.shadow").version(shadow)
            plugin("kover", "org.jetbrains.kotlinx.kover").version(kover)
        }
    }
}
