package com.diary.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.diary.models.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import io.ktor.server.response.*

fun Application.configureSecurity(config: ApplicationConfig) {
    val secret = config.property("jwt.secret").getString()
    val issuer = config.property("jwt.issuer").getString()
    val audience = config.property("jwt.audience").getString()
    val realm = config.property("jwt.realm").getString()
    val algo = Algorithm.HMAC256(secret)

    val verifier = JWT.require(algo).withAudience(audience).withIssuer(issuer).build()

    install(Authentication) {
        // Standard access token — rejects refresh tokens
        jwt("auth-jwt") {
            this.realm = realm
            verifier(verifier)
            validate { credential ->
                val type = credential.payload.getClaim("type").asString()
                val userId = credential.payload.getClaim("userId").asString()
                if (type == "access" && !userId.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, message = "Token is not valid or has expired"))
            }
        }

        // Refresh token — only accepts tokens with type=refresh
        jwt("auth-jwt-refresh") {
            this.realm = realm
            verifier(verifier)
            validate { credential ->
                val type = credential.payload.getClaim("type").asString()
                val userId = credential.payload.getClaim("userId").asString()
                if (type == "refresh" && !userId.isNullOrEmpty()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, message = "Refresh token is not valid or has expired"))
            }
        }
    }
}
