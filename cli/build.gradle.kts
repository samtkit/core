plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    implementation(libs.jCommander)
    testImplementation(testLibs.kotlin.test)
}

application {
    // Define the main class for the application.
    mainClass.set("tools.samt.cli.AppKt")
}

tasks.withType<Test> {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
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
