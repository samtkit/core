plugins {
    id("samt-core.kotlin-jvm")
    id("samt-core.library")
    alias(libs.plugins.kover)
}

val mavenName: String by extra("SAMT CodeGen")
val mavenDescription: String by extra("Call SAMT plugins to generate code from SAMT files.")

dependencies {
    implementation(project(":common"))
    implementation(project(":compiler"))
    api(project(":public-api"))
    testImplementation(project(":samt-config"))
}
