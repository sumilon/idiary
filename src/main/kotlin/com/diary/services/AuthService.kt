package com.diary.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.diary.models.*
import io.ktor.server.config.*
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class AuthService(config: ApplicationConfig) {

    private val secret        = config.property("jwt.secret").getString()
    private val issuer        = config.property("jwt.issuer").getString()
    private val audience      = config.property("jwt.audience").getString()
    private val expirationHours = config.property("jwt.expirationHours").getString().toLong()
    private val algorithm     = Algorithm.HMAC256(secret)

    // ── Password helpers ──

    fun hashPassword(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(10))

    fun verifyPassword(raw: String, hash: String): Boolean = BCrypt.checkpw(raw, hash)

    // ── Token generation ──

    fun generateToken(user: User): String =
        JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id)
            .withClaim("email", user.email)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationHours * 3_600_000))
            .sign(algorithm)

    // ── Auth operations ──

    suspend fun register(request: RegisterRequest): AuthResponse {
        require(request.email.isNotBlank() && request.password.isNotBlank() && request.name.isNotBlank()) {
            "All fields are required"
        }
        require(request.password.length >= 6) { "Password must be at least 6 characters" }
        require(request.email.contains("@"))  { "Invalid email address" }

        if (FirebaseService.getUserByEmail(request.email.lowercase()) != null) {
            throw IllegalArgumentException("Email already registered")
        }

        val user = User(
            id           = UUID.randomUUID().toString(),
            email        = request.email.lowercase().trim(),
            name         = request.name.trim(),
            passwordHash = hashPassword(request.password),
            createdAt    = System.currentTimeMillis()
        )

        FirebaseService.createUser(user)
        return AuthResponse(generateToken(user), UserDTO(user.id, user.email, user.name))
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = FirebaseService.getUserByEmail(request.email.lowercase().trim())
            ?: throw IllegalArgumentException("Invalid email or password")

        if (!verifyPassword(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid email or password")
        }

        return AuthResponse(generateToken(user), UserDTO(user.id, user.email, user.name))
    }

    suspend fun changePassword(userId: String, request: ChangePasswordRequest): Unit {
        require(request.currentPassword.isNotBlank()) { "Current password is required" }
        require(request.newPassword.length >= 6)      { "New password must be at least 6 characters" }
        require(request.currentPassword != request.newPassword) {
            "New password must differ from current password"
        }

        val user = FirebaseService.getUserById(userId)
            ?: throw NoSuchElementException("User not found")

        if (!verifyPassword(request.currentPassword, user.passwordHash)) {
            throw SecurityException("Current password is incorrect")
        }

        FirebaseService.updatePasswordHash(userId, hashPassword(request.newPassword))
    }
}
