package com.diary

import com.diary.plugins.*
import com.diary.services.FirebaseService
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    // Fail fast with clear messages if required env vars are missing
    requireEnv("JWT_SECRET")
    requireEnv("FIREBASE_PROJECT_ID")

    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        module()
    }.start(wait = true)
}

private fun requireEnv(name: String) {
    require(!System.getenv(name).isNullOrBlank()) {
        "Required environment variable '$name' is not set. Cannot start."
    }
}

fun Application.module() {

    val config = HoconApplicationConfig(ConfigFactory.load())
    val repo   = FirebaseService(config)

    configureSecurity(config)
    configureSerialization()
    configureCompression()
    configureStatusPages()
    configureRouting(config, repo)
}
