plugins {
    id("samt-core.kotlin-jvm")
    id("samt-core.library")
    alias(libs.plugins.kover)
}

val mavenName: String by extra("SAMT Compiler")
val mavenDescription: String by extra("Parse, analyze, and do whatever you desire with SAMT files.")

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))
}
