rootProject.name = "samt-core"
include("cli")

dependencyResolutionManagement {
    versionCatalogs {
        val kotlin = "1.8.10"
        val shadow = "8.1.0"
        val jCommander = "1.82"

        create("libs") {
            library("jCommander", "com.beust", "jcommander").version(jCommander)

            plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").version(kotlin)
            plugin("shadow", "com.github.johnrengelman.shadow").version(shadow)
        }

        create("testLibs") {
            library("kotlin-test", "org.jetbrains.kotlin", "kotlin-test-junit5").version(kotlin)
        }
    }
}
