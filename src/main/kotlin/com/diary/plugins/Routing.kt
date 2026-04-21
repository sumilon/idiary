package com.diary.plugins

import com.diary.routes.authRoutes
import com.diary.routes.diaryRoutes
import com.diary.routes.pageRoutes
import com.diary.services.AuthService
import com.diary.services.DiaryRepository
import com.diary.services.GrokAIService
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

fun Application.configureRouting(config: ApplicationConfig, repo: DiaryRepository) {
    val authService = AuthService(config, repo)
    val grokService = GrokAIService(config)

    environment.monitor.subscribe(ApplicationStopped) { grokService.close() }

    routing {
        staticResources("/static", "static")
        pageRoutes()
        route("/api") {
            authRoutes(authService)
            diaryRoutes(repo, grokService)
        }
    }
}
