plugins {
    id("samt-core.kotlin-jvm")
    `java-library`
    `maven-publish`
    signing
}

val mavenName: String by project.extra
val mavenDescription: String by project.extra

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            pom {
                name.set(provider { mavenName })
                description.set(provider { mavenDescription })
                url.set("https://github.com/samtkit/core")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/samtkit/core/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        name.set("Pascal Honegger")
                        email.set("pascal.honegger@samt.tools")
                        organization.set("Simple API Modeling Toolkit")
                        organizationUrl.set("https://github.com/samtkit")
                    }
                    developer {
                        name.set("Marcel Joss")
                        email.set("marcel.joss@samt.tools")
                        organization.set("Simple API Modeling Toolkit")
                        organizationUrl.set("https://github.com/samtkit")
                    }
                    developer {
                        name.set("Leonard Schütz")
                        email.set("leonard.schuetz@samt.tools")
                        organization.set("Simple API Modeling Toolkit")
                        organizationUrl.set("https://github.com/samtkit")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/samtkit/core.git")
                    developerConnection.set("scm:git:ssh://github.com/samtkit/core.git")
                    url.set("https://github.com/samtkit/core")
                }
            }
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = if (version.toString().endsWith("-SNAPSHOT")) {
                uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            } else {
                uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            }
            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

signing {
    isRequired = !version.toString().endsWith("-SNAPSHOT")
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications["maven"])
}
