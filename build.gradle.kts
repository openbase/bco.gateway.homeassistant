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
    implementation("org.openbase:bco.dal.control")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
