plugins {
    @Suppress("DSL_SCOPE_VIOLATION") // Fixed in Gradle 8.1, see https://github.com/gradle/gradle/issues/22797
    alias(libs.plugins.kover)
}

repositories {
    mavenCentral()
}

koverMerged {
    enable()
    htmlReport {}
    xmlReport {}
    verify {
        rule {
            name = "Minimal line coverage rate in percent"
            bound {
                minValue = 80
            }
        }
    }
}
