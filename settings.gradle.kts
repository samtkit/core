rootProject.name = "samt-core"
include(
    ":common",
    ":cli",
    ":compiler",
    ":language-server",
    ":samt-config",
    ":codegen",
    ":public-api",
)

dependencyResolutionManagement {
    versionCatalogs {
        val kotlin = "1.9.22"
        val shadow = "8.1.1"
        val jCommander = "1.82"
        val mordant = "2.2.0"
        val kotlinxSerialization = "1.6.2"
        val kamlVersion = "0.56.0"
        val kover = "0.7.4"
        val gitVersioning = "6.4.3"
        val lsp4j = "0.21.1"
        val dokka = "1.9.10"

        create("libs") {
            version("kotlin", kotlin)
            library("jCommander", "com.beust", "jcommander").version(jCommander)
            library("mordant", "com.github.ajalt.mordant", "mordant").version(mordant)
            library("kotlinx.serialization-json", "org.jetbrains.kotlinx", "kotlinx-serialization-json").version(kotlinxSerialization)
            library("kotlinx.serialization-yaml", "com.charleskorn.kaml", "kaml").version(kamlVersion)
            library("lsp4j", "org.eclipse.lsp4j", "org.eclipse.lsp4j").version(lsp4j)

            plugin("shadow", "com.github.johnrengelman.shadow").version(shadow)
            plugin("kover", "org.jetbrains.kotlinx.kover").version(kover)
            plugin("versioning", "me.qoomon.git-versioning").version(gitVersioning)
            plugin("dokka", "org.jetbrains.dokka").version(dokka)
        }
    }
}
