plugins {
    application
    id("samt-core.kotlin-conventions")
    @Suppress("DSL_SCOPE_VIOLATION") // Fixed in Gradle 8.1, see https://github.com/gradle/gradle/issues/22797
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(libs.jCommander)
}

application {
    // Define the main class for the application.
    mainClass.set("tools.samt.cli.AppKt")
}

tasks {
    shadowJar {
        archiveBaseName.set("samt-cli")
        archiveClassifier.set("")
        manifest {
            attributes("Main-Class" to "tools.samt.cli.AppKt")
        }
    }
}
