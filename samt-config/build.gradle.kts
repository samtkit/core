plugins {
    id("samt-core.kotlin-jvm")
    id("samt-core.library")
    alias(libs.plugins.kover)
}

val mavenName: String by extra("SAMT Config")
val mavenDescription: String by extra("SAMT Configuration Parser")

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.serialization.yaml)
}
