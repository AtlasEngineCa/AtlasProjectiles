plugins {
    id("java")
    `maven-publish`
    signing
}

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
        groupId = "ca.atlasengine"
        artifactId = "atlas-projectiles"
        version = "2.1.6"

        from(components["java"])
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

    compileOnly("net.minestom:minestom:2026.01.01-1.21.11")
    testImplementation("net.minestom:minestom:2026.01.01-1.21.11")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
    enabled = false
}
