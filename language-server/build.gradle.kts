plugins {
    application
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(libs.jCommander)
    implementation(project(":common"))
    implementation(project(":lexer"))
    implementation(project(":parser"))
    implementation(project(":semantic"))
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
