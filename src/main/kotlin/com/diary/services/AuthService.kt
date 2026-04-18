package com.diary.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.diary.models.*
import io.ktor.server.config.*
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class AuthService(config: ApplicationConfig) {
    private val secret = config.property("jwt.secret").getString()
    private val issuer = config.property("jwt.issuer").getString()
    private val audience = config.property("jwt.audience").getString()
    // HOCON integers must be read as getString() then parsed, or use the raw string path
    private val expirationHours: Long = config.propertyOrNull("jwt.expirationHours")
        ?.getString()?.toLongOrNull() ?: 24L
    private val algorithm = Algorithm.HMAC256(secret)

    fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(10))

    fun verifyPassword(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)

    fun generateToken(user: User): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationHours * 3600 * 1000))
            .sign(algorithm)
    }

    suspend fun register(request: RegisterRequest): AuthResponse {
        if (request.email.isBlank() || request.password.isBlank() || request.name.isBlank()) {
            throw IllegalArgumentException("All fields are required")
        }
        if (request.password.length < 6) {
            throw IllegalArgumentException("Password must be at least 6 characters")
        }
        if (!request.email.contains("@")) {
            throw IllegalArgumentException("Invalid email address")
        }

        val existing = FirebaseService.getUserByEmail(request.email.lowercase())
        if (existing != null) {
            throw IllegalArgumentException("Email already registered")
        }

        val user = User(
            id = UUID.randomUUID().toString(),
            email = request.email.lowercase().trim(),
            name = request.name.trim(),
            passwordHash = hashPassword(request.password),
            createdAt = System.currentTimeMillis()
        )

        FirebaseService.createUser(user)
        val token = generateToken(user)
        return AuthResponse(token = token, user = UserDTO(user.id, user.email, user.name))
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = FirebaseService.getUserByEmail(request.email.lowercase().trim())
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!verifyPassword(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        val token = generateToken(user)
        return AuthResponse(token = token, user = UserDTO(user.id, user.email, user.name))
    }
}
