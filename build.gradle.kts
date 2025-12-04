import java.net.URL

plugins {
    application
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.0"
}

group = "org.openbase"
version = "1.0-SNAPSHOT"
description = "A gateway that enables seamless integration between BaseCubeOne and Assistant."

val bcoVersion: String by project
val jerseyVersion: String by project
val ktorVersion: String by project
val logbackVersion: String by project

repositories {
    mavenLocal()
    maven {
        url  = uri("https://repo1.maven.org/maven2")
    }
    google()
}

dependencies {
    implementation("org.openbase:bco.dal.control:$bcoVersion")
    implementation("org.glassfish.jersey.core:jersey-client:$jerseyVersion")
    implementation("org.glassfish.jersey.core:jersey-common:$jerseyVersion")
    implementation("org.glassfish.jersey.inject:jersey-hk2:$jerseyVersion")
    implementation("org.glassfish.jersey.security:oauth2-client:$jerseyVersion")
    implementation("org.glassfish.jersey.media:jersey-media-json-jackson:$jerseyVersion")

    // Kotlin dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // JSON query library (JsonPath) for expressive querying of options.json
    implementation("com.jayway.jsonpath:json-path:2.8.0")
    testImplementation(kotlin("test"))
    // https://mvnrepository.com/artifact/io.mockk/mockk
    testImplementation("io.mockk:mockk:1.14.4")
    // https://mvnrepository.com/artifact/org.amshove.kluent/kluent
    testImplementation("org.amshove.kluent:kluent:1.73")
    // https://mvnrepository.com/artifact/io.mockk/mockk
    testImplementation("io.mockk:mockk:1.14.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("org.openbase.bco.gateway.homeassistant.HassGatewayLauncher")
}

application.applicationName = "bco-gateway-homeassistant"

distributions {
    main {
        distributionBaseName.set("bco-gateway-homeassistant")
    }
}
