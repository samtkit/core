plugins {
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlinx.serialization.yaml)
}
