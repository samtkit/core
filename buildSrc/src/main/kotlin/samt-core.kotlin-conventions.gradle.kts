plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") apply false
}

apply(plugin = "kover")

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
