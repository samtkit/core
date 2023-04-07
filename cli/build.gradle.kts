plugins {
    application
    id("samt-core.kotlin-conventions")
    @Suppress("DSL_SCOPE_VIOLATION") // Fixed in Gradle 8.1, see https://github.com/gradle/gradle/issues/22797
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(libs.jCommander)
    implementation(libs.mordant)
    implementation(project(":common"))
    implementation(project(":lexer"))
    implementation(project(":parser"))
}

application {
    // Define the main class for the application.
    mainClass.set("tools.samt.cli.AppKt")
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>("compileKotlin").configure {
    // Allow usage of currently experimental Mordant API, but we accept the risk given it's just to make the CLI look nice
    compilerOptions.freeCompilerArgs.add("-opt-in=com.github.ajalt.mordant.terminal.ExperimentalTerminalApi")
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
