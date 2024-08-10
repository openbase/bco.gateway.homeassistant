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
