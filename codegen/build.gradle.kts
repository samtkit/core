plugins {
    id("samt-core.kotlin-conventions")
    alias(libs.plugins.kover)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":parser"))
    implementation(project(":semantic"))
    implementation(project(":public-api"))
    testImplementation(project(":lexer"))
}
