package com.diary

import com.diary.plugins.*
import com.diary.services.FirebaseService
import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toInt() ?: 8080) {
        module()
    }.start(wait = true)
}

fun Application.module() {

    val config = HoconApplicationConfig(ConfigFactory.load())

    val projectId = config
        .propertyOrNull("firebase.projectId")
        ?.getString()

    println("Project ID = $projectId")

    FirebaseService.initialize(config)

    configureSecurity(config)
    configureSerialization()
    configureCompression()
    configureStatusPages()
    configureRouting(config)
}
