plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.openbase"
version = "1.0-SNAPSHOT"
val bcoVersion: String by project
val jakartaVersion: String by project
val jerseyVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openbase:bco.dal.control:$bcoVersion")
    implementation("jakarta.activation:jakarta.activation-api:$jakartaVersion")
    implementation("org.glassfish.jersey.core:jersey-client:$jerseyVersion")
    implementation("org.glassfish.jersey.inject:jersey-hk2:$jerseyVersion")
    implementation("org.glassfish.jersey.media:jersey-media-sse:$jerseyVersion")
    implementation("org.glassfish.jersey.security:oauth2-client:$jerseyVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
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
