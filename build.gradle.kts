plugins {
    alias(libs.plugins.kover)
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
