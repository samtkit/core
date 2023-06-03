plugins {
    application
    id("samt-core.kotlin-jvm")
    alias(libs.plugins.shadow)
    kotlin("plugin.serialization")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.jCommander)
    implementation(libs.mordant)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":common"))
    implementation(project(":compiler"))
    implementation(project(":samt-config"))
    implementation(project(":codegen"))
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
        archiveVersion.set("")
        manifest {
            attributes("Main-Class" to "tools.samt.cli.AppKt")
        }
    }
}
