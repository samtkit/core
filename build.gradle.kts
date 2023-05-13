plugins {
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
}

dependencies {
    kover(project(":common"))
    kover(project(":lexer"))
    kover(project(":parser"))
    kover(project(":semantic"))
    kover(project(":cli"))
    kover(project(":language-server"))
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
