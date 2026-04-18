package com.diary.routes

import com.diary.models.*
import com.diary.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {

        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = authService.register(request)
            call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = response))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request)
            call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = response))
        }

        authenticate("auth-jwt") {
            post("/change-password") {
                val userId  = call.userId()
                val request = call.receive<ChangePasswordRequest>()
                authService.changePassword(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true, message = "Password changed successfully"))
            }
        }
    }
}

/** Extracts the authenticated user's ID from the JWT principal. */
fun ApplicationCall.userId(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw SecurityException("Unauthorized")
    return principal.payload.getClaim("userId").asString()
        ?: throw SecurityException("Invalid token")
}
