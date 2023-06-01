plugins {
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))
}
