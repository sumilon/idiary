plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("com.gradleup.shadow") version "8.3.8"
    application
}

group = "com.diary"
version = "1.0.0"

application {
    mainClass.set("com.diary.ApplicationKt")
    applicationDefaultJvmArgs = listOf(
        "-Xmx192m",
        "-Xms32m",
        "-XX:+UseSerialGC",
        "-XX:MaxMetaspaceSize=96m",
        "-Djava.awt.headless=true"
    )
}


repositories {
    mavenCentral()
    google()
}

val ktorVersion = "2.3.13"
val firebaseVersion = "9.4.2"
val kotlinVersion = "1.9.23"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")

    // Ktor Client (for Groq AI calls)
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    // Firebase Admin SDK
    // Exclude grpc-netty to avoid Netty version conflict with Ktor's Netty server.
    // grpc-netty-shaded provides an isolated (relocated) Netty so both can coexist safely.
    implementation("com.google.firebase:firebase-admin:$firebaseVersion") {
        exclude(group = "io.grpc", module = "grpc-netty")
    }
    implementation("io.grpc:grpc-netty-shaded:1.76.0")

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.25")

    // Kotlin Serialization + Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

// Force-override vulnerable transitive dependencies that cannot be resolved via constraints
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.protobuf" && requested.name == "protobuf-java") {
            useVersion("3.25.5")
            because("CVE-2024-7254: protobuf-java < 3.25.5 is vulnerable")
        }
        if (requested.group == "com.fasterxml.jackson.core") {
            useVersion("2.18.6")
            because("GHSA-72hv-8253-57qq: jackson-core < 2.18.6 is vulnerable")
        }
        if (requested.group == "io.netty") {
            useVersion("4.1.132.Final")
            because("CVE-2025-55163/58056/58057/CVE-2024-47535/CVE-2026-33871: Netty < 4.1.132.Final is vulnerable")
        }
        if (requested.group == "ch.qos.logback") {
            useVersion("1.5.25")
            because("CVE-2024-12798/CVE-2025-11226/CVE-2026-1225/CVE-2024-12801: logback < 1.5.25 is vulnerable")
        }
        if (requested.group == "io.grpc" && requested.name == "grpc-netty-shaded") {
            useVersion("1.76.0")
            because("CVE-2025-55163: grpc-netty-shaded < 1.76.0 bundles vulnerable Netty")
        }
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("diary-app")
        archiveClassifier.set("")
        archiveVersion.set("")
        // Merges META-INF/services/* — required for gRPC/Firebase service providers
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
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}
