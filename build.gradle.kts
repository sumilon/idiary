plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "com.diary"
version = "1.0.0"

application {
    mainClass.set("com.diary.ApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx256m",
        "-Xms64m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=100"
    )
}

repositories {
    mavenCentral()
    google()
}

val ktorVersion = "2.3.10"
val firebaseVersion = "9.3.0"
val kotlinVersion = "1.9.23"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")

    // Ktor Client (for Groq API calls)
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")

    // Firebase Admin SDK
    implementation("com.google.firebase:firebase-admin:$firebaseVersion")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

tasks {
    shadowJar {
        archiveBaseName.set("diary-app")
        archiveClassifier.set("")
        archiveVersion.set("")
        // Critical: merges META-INF/services/* files instead of overwriting them.
        // Without this, gRPC (used by Firebase) loses its service provider
        // registrations and throws "Could not find policy 'pick_first'" at runtime.
        mergeServiceFiles()
        manifest {
            attributes["Main-Class"] = "com.diary.ApplicationKt"
        }
    }

    build {
        dependsOn(shadowJar)
    }
}

kotlin {
    jvmToolchain(17)
}
