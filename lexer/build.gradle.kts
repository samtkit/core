plugins {
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    testImplementation(kotlin("reflect"))
}
