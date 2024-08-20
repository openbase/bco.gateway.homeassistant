plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "1.9.24"
    id("org.springframework.boot") version "3.1.0"
    id("io.spring.dependency-management") version "1.1.0"
}

group = "org.openbase"
version = "1.0-SNAPSHOT"
description = "BaseCubeOne Homeassistant Device Manager"

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
    implementation("org.webjars:webjars-locator-core")

    // Spring Boot starter for web applications, includes Spring MVC and embedded Tomcat
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring Boot starter for WebSocket support
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Spring Boot starter for testing
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }
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

application {
    mainClass.set("org.openbase.bco.device.hass.HassDeviceManagerLauncher")
}

application.applicationName = "bco-device-hass"

distributions {
    main {
        distributionBaseName.set("bco-device-hass")
    }
}
