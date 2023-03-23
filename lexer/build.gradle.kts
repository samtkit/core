plugins {
    id("samt-core.kotlin-conventions")
}

dependencies {
    implementation(project(":common"))
    testImplementation(kotlin("reflect"))
}
