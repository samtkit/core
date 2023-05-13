plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") apply false
}

// does not work with Kover 0.7.0-Beta for some reason
//apply(plugin = "kover")

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

repositories {
    mavenCentral()
}

group = "samt-core"
