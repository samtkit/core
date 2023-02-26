plugins {
    application
    id("samt-core.kotlin-conventions")
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
