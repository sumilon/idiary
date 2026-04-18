package com.diary.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val passwordHash: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class DiaryEntry(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ── Auth models ──

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDTO
)

@Serializable
data class UserDTO(
    val id: String,
    val email: String,
    val name: String
)

// ── Diary models ──

@Serializable
data class CreateEntryRequest(
    val title: String,
    val content: String,
    val mood: String? = null
)

@Serializable
data class UpdateEntryRequest(
    val title: String,
    val content: String,
    val mood: String? = null
)

// ── AI models ──

@Serializable
data class AiRewriteRequest(
    val content: String,
    val instruction: String = "improve"
)

@Serializable
data class AiRewriteResponse(
    val rewritten: String
)

// ── Generic response ──

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

// ── List projection ──

@Serializable
data class DiaryListItem(
    val id: String,
    val title: String,
    val preview: String,
    val mood: String?,
    val createdAt: Long,
    val updatedAt: Long
)
