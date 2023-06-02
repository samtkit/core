plugins {
    id("samt-core.kotlin-jvm")
    id("samt-core.library")
}

val mavenName: String by extra("SAMT Public API")
val mavenDescription: String by extra("Public API for creating custom SAMT plugins.")
