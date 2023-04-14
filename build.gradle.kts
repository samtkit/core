plugins {
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
