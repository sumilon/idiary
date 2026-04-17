package com.diary.plugins

import com.diary.routes.authRoutes
import com.diary.routes.diaryRoutes
import com.diary.routes.pageRoutes
import com.diary.services.AuthService
import com.diary.services.GrokAIService
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: ApplicationConfig) {
    val authService = AuthService(config)
    val grokService = GrokAIService(config)

    routing {
        // Serve static files
        staticResources("/static", "static")

        // Page routes (HTML pages)
        pageRoutes()

        // API routes
        route("/api") {
            authRoutes(authService)
            diaryRoutes(grokService)
        }
    }
}
