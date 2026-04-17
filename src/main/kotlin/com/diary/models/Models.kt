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
    val content: String = "",   // single source of truth — stores either user text or AI-rewritten text
    val mood: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

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

@Serializable
data class CreateEntryRequest(
    val title: String,
    val content: String,
    val mood: String? = null
)

@Serializable
data class UpdateEntryRequest(
    val title: String,
    val content: String,        // caller sends whichever content to persist (user or AI)
    val mood: String? = null
)

@Serializable
data class AiRewriteRequest(
    val content: String,
    val instruction: String = "improve"
)

@Serializable
data class AiRewriteResponse(
    val rewritten: String       // only the AI result; caller decides whether to save it
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)

@Serializable
data class DiaryListItem(
    val id: String,
    val title: String,
    val preview: String,
    val mood: String?,
    val createdAt: Long,
    val updatedAt: Long
)
