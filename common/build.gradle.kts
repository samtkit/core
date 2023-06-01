plugins {
    id("samt-core.kotlin-jvm")
    id("samt-core.library")
    alias(libs.plugins.kover)
}

val mavenName: String by extra("SAMT Common")
val mavenDescription: String by extra("Common SAMT module")
