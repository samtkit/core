plugins {
    application
    id("samt-core.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.jCommander)
    implementation(libs.lsp4j)
    implementation(project(":common"))
    implementation(project(":compiler"))
    implementation(project(":samt-config"))
}

application {
    // Define the main class for the application.
    mainClass.set("tools.samt.ls.AppKt")
}

tasks {
    shadowJar {
        archiveBaseName.set("samt-ls")
        archiveClassifier.set("")
        archiveVersion.set("")
        manifest {
            attributes("Main-Class" to "tools.samt.ls.AppKt")
        }
    }
}
