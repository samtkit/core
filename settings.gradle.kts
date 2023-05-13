rootProject.name = "samt-core"
include(
    ":common",
    ":cli",
    ":lexer",
    ":parser",
    ":semantic",
    ":language-server"
)

dependencyResolutionManagement {
    versionCatalogs {
        val kotlin = "1.8.21"
        val shadow = "8.1.1"
        val jCommander = "1.82"
        val mordant = "2.0.0-beta13"
        val kotlinxSerialization = "1.5.0"
        val kover = "0.7.0-Beta"
        val lsp4j = "0.20.1"

        create("libs") {
            version("kotlin", kotlin)
            library("jCommander", "com.beust", "jcommander").version(jCommander)
            library("mordant", "com.github.ajalt.mordant", "mordant").version(mordant)
            library("kotlinx.serialization-json", "org.jetbrains.kotlinx", "kotlinx-serialization-json").version(kotlinxSerialization)
            library("lsp4j", "org.eclipse.lsp4j", "org.eclipse.lsp4j").version(lsp4j)

            plugin("shadow", "com.github.johnrengelman.shadow").version(shadow)
            plugin("kover", "org.jetbrains.kotlinx.kover").version(kover)
        }
    }
}
