plugins {
    kotlin("jvm") version "2.0.0"
}

group = "org.openbase"
version = "1.0-SNAPSHOT"
val ktorVersion: String by project
val logbackVersion: String by project

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openbase:bco.dal.control:3.2-SNAPSHOT")
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
