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
            val request    = call.receive<RegisterRequest>()
            val authResult = authService.register(request)
            call.respond(HttpStatusCode.Created, ApiResponse<AuthResponse>(success = true, data = authResult))
        }

        post("/login") {
            val request    = call.receive<LoginRequest>()
            val authResult = authService.login(request)
            call.respond(HttpStatusCode.OK, ApiResponse<AuthResponse>(success = true, data = authResult))
        }

        // Exchange a valid refresh token for a fresh token pair (rotation)
        authenticate("auth-jwt-refresh") {
            post("/refresh") {
                val userId     = call.userId()
                val authResult = authService.refreshToken(userId)
                call.respond(HttpStatusCode.OK, ApiResponse<AuthResponse>(success = true, data = authResult))
            }
        }

        authenticate("auth-jwt") {
            post("/change-password") {
                val userId  = call.userId()
                val request = call.receive<ChangePasswordRequest>()
                authService.changePassword(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true, message = "Password changed successfully"))
            }

            // Profile management
            get("/profile") {
                val userId = call.userId()
                val user   = authService.getProfile(userId)
                call.respond(HttpStatusCode.OK, ApiResponse<UserDTO>(success = true, data = user))
            }

            put("/profile") {
                val userId  = call.userId()
                val request = call.receive<UpdateProfileRequest>()
                val updated = authService.updateProfile(userId, request)
                call.respond(HttpStatusCode.OK, ApiResponse<UserDTO>(success = true, data = updated))
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
