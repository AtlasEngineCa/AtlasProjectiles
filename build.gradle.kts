plugins {
    id("java")
    `maven-publish`
    signing
}

group = "ca.atlasengine"
version = "3.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25

    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
}

publishing {
    publications.create<MavenPublication>("maven") {
        groupId = project.group.toString()
        artifactId = "atlas-projectiles"
        version = project.version.toString()

        from(components["java"])

        pom {
            name = "AtlasProjectiles"
            description = "Projectile movement and collision utilities for Minestom servers."
            url = "https://github.com/AtlasEngineCa/AtlasProjectiles"

            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }

            scm {
                connection = "scm:git:git://github.com/AtlasEngineCa/AtlasProjectiles.git"
                developerConnection = "scm:git:ssh://git@github.com/AtlasEngineCa/AtlasProjectiles.git"
                url = "https://github.com/AtlasEngineCa/AtlasProjectiles"
            }
        }
    }

    repositories {
        maven {
            name = "AtlasEngine"
            url = uri("https://reposilite.atlasengine.ca/public")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.2")

    compileOnly("net.minestom:minestom:2026.01.01-1.21.11")
    testImplementation("net.minestom:minestom:2026.01.01-1.21.11")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
