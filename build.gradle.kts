plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.openbase"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("org.openbase:bco.dal.control:3.2-SNAPSHOT")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    // https://mvnrepository.com/artifact/org.glassfish.jersey.core/jersey-client
    implementation("org.glassfish.jersey.core:jersey-client:3.1.8")
    implementation("org.glassfish.jersey.inject:jersey-hk2:3.1.8")
    implementation("org.glassfish.jersey.media:jersey-media-sse:3.1.8")
    implementation("org.glassfish.jersey.security:oauth2-client:3.1.8")
    testImplementation(kotlin("test"))
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven {
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
