plugins {
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":compiler"))
    api(project(":public-api"))
    testImplementation(project(":samt-config"))
}
