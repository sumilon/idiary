package com.diary.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.diary.models.*
import io.ktor.server.config.*
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class AuthService(config: ApplicationConfig, private val repo: DiaryRepository) {

    private val secret               = config.property("jwt.secret").getString()
    private val issuer               = config.property("jwt.issuer").getString()
    private val audience             = config.property("jwt.audience").getString()
    private val expirationHours      = config.property("jwt.expirationHours").getString().toLong()
    private val refreshExpirationDays = config.propertyOrNull("jwt.refreshExpirationDays")?.getString()?.toLong() ?: 30L
    private val algorithm            = Algorithm.HMAC256(secret)

    companion object {
        // RFC 5322 simplified email regex
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidEmail(email: String) = EMAIL_REGEX.matches(email)

        /**
         * Password rules: min 8 chars, at least one uppercase, one lowercase, one digit.
         */
        fun isStrongPassword(password: String): Boolean =
            password.length >= 8 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() }
    }

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
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + expirationHours * 3_600_000))
            .sign(algorithm)

    fun generateRefreshToken(user: User): String =
        JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", user.id)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshExpirationDays * 86_400_000))
            .sign(algorithm)

    private fun authResponse(user: User) =
        AuthResponse(generateToken(user), generateRefreshToken(user), UserDTO(user.id, user.email, user.name))

    // ── Auth operations ──

    suspend fun register(request: RegisterRequest): AuthResponse {
        require(request.name.isNotBlank())                  { "Name is required" }
        require(isValidEmail(request.email))                { "Invalid email address" }
        require(isStrongPassword(request.password))         {
            "Password must be at least 8 characters and contain uppercase, lowercase, and a digit"
        }
        if (repo.getUserByEmail(request.email.lowercase()) != null)
            throw IllegalArgumentException("Email already registered")

        val user = User(
            id           = UUID.randomUUID().toString(),
            email        = request.email.lowercase().trim(),
            name         = request.name.trim(),
            passwordHash = hashPassword(request.password),
            createdAt    = System.currentTimeMillis()
        )
        repo.createUser(user)
        return authResponse(user)
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = repo.getUserByEmail(request.email.lowercase().trim())
            ?: throw IllegalArgumentException("Invalid email or password")
        if (!verifyPassword(request.password, user.passwordHash))
            throw IllegalArgumentException("Invalid email or password")
        return authResponse(user)
    }

    suspend fun refreshToken(userId: String): AuthResponse {
        val user = repo.getUserById(userId)
            ?: throw NoSuchElementException("User not found")
        return authResponse(user)
    }

    suspend fun changePassword(userId: String, request: ChangePasswordRequest) {
        require(request.currentPassword.isNotBlank())       { "Current password is required" }
        require(isStrongPassword(request.newPassword))      {
            "New password must be at least 8 characters and contain uppercase, lowercase, and a digit"
        }
        require(request.currentPassword != request.newPassword) { "New password must differ from current password" }

        val user = repo.getUserById(userId)
            ?: throw NoSuchElementException("User not found")
        if (!verifyPassword(request.currentPassword, user.passwordHash))
            throw SecurityException("Current password is incorrect")

        repo.updatePasswordHash(userId, hashPassword(request.newPassword))
    }

    suspend fun getProfile(userId: String): UserDTO {
        val user = repo.getUserById(userId) ?: throw NoSuchElementException("User not found")
        return UserDTO(user.id, user.email, user.name)
    }

    suspend fun updateProfile(userId: String, request: UpdateProfileRequest): UserDTO {
        require(request.name.isNotBlank()) { "Name cannot be blank" }
        repo.updateUserName(userId, request.name.trim())
        val user = repo.getUserById(userId) ?: throw NoSuchElementException("User not found")
        return UserDTO(user.id, user.email, user.name)
    }
}
