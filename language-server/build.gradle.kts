plugins {
    application
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.jCommander)
    implementation(libs.lsp4j)
    implementation(project(":common"))
    implementation(project(":lexer"))
    implementation(project(":parser"))
    implementation(project(":semantic"))
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
        manifest {
            attributes("Main-Class" to "tools.samt.ls.AppKt")
        }
    }
}
