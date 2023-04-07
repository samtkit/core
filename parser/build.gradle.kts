plugins {
    id("samt-core.kotlin-conventions")
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(project(":common"))
    implementation(project(":lexer"))
}
