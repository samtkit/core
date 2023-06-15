plugins {
    alias(libs.plugins.kover)
    alias(libs.plugins.versioning)
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

dependencies {
    kover(project(":common"))
    kover(project(":compiler"))
    kover(project(":cli"))
    kover(project(":language-server"))
    kover(project(":samt-config"))
    kover(project(":codegen"))
}

koverReport {
    defaults {
        verify {
            rule {
                bound {
                    minValue = 80
                }
            }
        }
    }
}

version = "0.0.0-SNAPSHOT"
gitVersioning.apply {
    refs {
        branch(".+") {
            version = "\${ref}-SNAPSHOT"
        }
        tag("v(?<version>.*)") {
            version = "\${ref.version}"
        }
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
