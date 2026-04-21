package com.diary.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.diary.models.*
import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Unit tests for [AuthService].
 * Uses [InMemoryDiaryRepository] — zero Firebase dependency.
 */
class AuthServiceTest {

    private val config = MapApplicationConfig(
        "jwt.secret"                to "test-secret-key-that-is-long-enough",
        "jwt.issuer"                to "diary-app",
        "jwt.audience"              to "diary-users",
        "jwt.realm"                 to "Diary App",
        "jwt.expirationHours"       to "24",
        "jwt.refreshExpirationDays" to "30"
    )

    private lateinit var repo: InMemoryDiaryRepository
    private lateinit var service: AuthService

    @BeforeTest
    fun setup() {
        repo    = InMemoryDiaryRepository()
        service = AuthService(config, repo)
    }

    // ── Email validation ──

    @Test
    fun `valid emails pass`() {
        assertTrue(AuthService.isValidEmail("user@example.com"))
        assertTrue(AuthService.isValidEmail("user.name+tag@sub.domain.org"))
    }

    @Test
    fun `invalid emails fail`() {
        assertFalse(AuthService.isValidEmail("notanemail"))
        assertFalse(AuthService.isValidEmail("missing@tld"))
        assertFalse(AuthService.isValidEmail("@nodomain.com"))
        assertFalse(AuthService.isValidEmail(""))
    }

    // ── Password strength ──

    @Test
    fun `strong passwords pass`() {
        assertTrue(AuthService.isStrongPassword("Password1"))
        assertTrue(AuthService.isStrongPassword("Abc12345"))
    }

    @Test
    fun `weak passwords fail`() {
        assertFalse(AuthService.isStrongPassword("short1A"))      // < 8 chars
        assertFalse(AuthService.isStrongPassword("nouppercase1")) // no uppercase
        assertFalse(AuthService.isStrongPassword("NOLOWERCASE1")) // no lowercase
        assertFalse(AuthService.isStrongPassword("NoDigitsHere")) // no digit
    }

    // ── Register ──

    @Test
    fun `register creates user and returns tokens`() = runBlocking {
        val response = service.register(RegisterRequest("user@example.com", "Password1", "Alice"))

        assertTrue(response.token.isNotBlank())
        assertTrue(response.refreshToken.isNotBlank())
        assertEquals("user@example.com", response.user.email)
        assertEquals("Alice", response.user.name)
        assertEquals(1, repo.users.size)
    }

    @Test
    fun `register rejects duplicate email`() = runBlocking<Unit> {
        service.register(RegisterRequest("dup@example.com", "Password1", "Alice"))
        assertFailsWith<IllegalArgumentException> {
            service.register(RegisterRequest("DUP@example.com", "Password1", "Bob"))
        }
    }

    @Test
    fun `register rejects invalid email`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> {
            service.register(RegisterRequest("bad-email", "Password1", "Alice"))
        }
    }

    @Test
    fun `register rejects weak password`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> {
            service.register(RegisterRequest("user@example.com", "weakpw", "Alice"))
        }
    }

    // ── Login ──

    @Test
    fun `login returns tokens for valid credentials`() = runBlocking {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val response = service.login(LoginRequest("user@example.com", "Password1"))

        assertTrue(response.token.isNotBlank())
        assertTrue(response.refreshToken.isNotBlank())
    }

    @Test
    fun `login rejects wrong password`() = runBlocking<Unit> {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        assertFailsWith<IllegalArgumentException> {
            service.login(LoginRequest("user@example.com", "WrongPass1"))
        }
    }

    @Test
    fun `login rejects unknown email`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> {
            service.login(LoginRequest("ghost@example.com", "Password1"))
        }
    }

    // ── Token types ──

    @Test
    fun `access token has type=access claim`() = runBlocking {
        val response = service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val algo     = Algorithm.HMAC256("test-secret-key-that-is-long-enough")
        val decoded  = JWT.require(algo).build().verify(response.token)
        assertEquals("access", decoded.getClaim("type").asString())
    }

    @Test
    fun `refresh token has type=refresh claim`() = runBlocking {
        val response = service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val algo     = Algorithm.HMAC256("test-secret-key-that-is-long-enough")
        val decoded  = JWT.require(algo).build().verify(response.refreshToken)
        assertEquals("refresh", decoded.getClaim("type").asString())
    }

    // ── Change password ──

    @Test
    fun `change password succeeds with correct current password`() = runBlocking<Unit> {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val userId = repo.users.values.first().id
        service.changePassword(userId, ChangePasswordRequest("Password1", "NewPass99"))
        // Should now login with new password — verifies the new hash was persisted
        val response = service.login(LoginRequest("user@example.com", "NewPass99"))
        assertTrue(response.token.isNotBlank())
    }

    @Test
    fun `change password rejects same password`() = runBlocking<Unit> {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val userId = repo.users.values.first().id
        assertFailsWith<IllegalArgumentException> {
            service.changePassword(userId, ChangePasswordRequest("Password1", "Password1"))
        }
    }

    // ── Profile ──

    @Test
    fun `updateProfile changes name`() = runBlocking {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val userId  = repo.users.values.first().id
        val updated = service.updateProfile(userId, UpdateProfileRequest("Bob"))
        assertEquals("Bob", updated.name)
        assertEquals("Bob", repo.users[userId]?.name)
    }

    @Test
    fun `getProfile returns correct user dto`() = runBlocking {
        service.register(RegisterRequest("user@example.com", "Password1", "Alice"))
        val userId  = repo.users.values.first().id
        val profile = service.getProfile(userId)
        assertEquals("user@example.com", profile.email)
        assertEquals("Alice", profile.name)
    }
}
