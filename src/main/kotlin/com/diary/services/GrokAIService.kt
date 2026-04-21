package com.diary.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GrokAIService(config: ApplicationConfig) {

    private val apiKey  = config.propertyOrNull("grok.apiKey")?.getString().orEmpty()
    private val baseUrl = config.propertyOrNull("grok.baseUrl")?.getString() ?: "https://api.groq.com/openai/v1"
    private val model   = config.propertyOrNull("grok.model")?.getString() ?: "llama3-8b-8192"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun rewriteContent(content: String, instruction: String): String {
        if (apiKey.isBlank()) throw IllegalStateException("AI service is not configured")

        val systemPrompt = "You are a helpful diary writing assistant. Help users improve their diary entries " +
            "while preserving their personal voice and emotions. Return ONLY the improved text — no explanations, no preamble."

        val userPrompt = when (instruction.lowercase()) {
            "improve"  -> "Please improve this diary entry to make it more readable and well-written:\n\n$content"
            "grammar"  -> "Please fix grammar and spelling errors in this diary entry:\n\n$content"
            "expand"   -> "Please expand and enrich this diary entry with more detail and expression:\n\n$content"
            "shorten"  -> "Please make this diary entry more concise while keeping the key points:\n\n$content"
            "formal"   -> "Please rewrite this diary entry in a more formal, eloquent style:\n\n$content"
            else       -> "Please $instruction this diary entry:\n\n$content"
        }

        return try {
            val response = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(GrokRequest(
                    model    = model,
                    messages = listOf(GrokMessage("system", systemPrompt), GrokMessage("user", userPrompt))
                ))
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw Exception("Groq API error: ${response.status}")
            json.decodeFromString<GrokResponse>(body).choices?.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")
        } catch (e: Exception) {
            throw Exception("AI service temporarily unavailable. Please try again.", e)
        }
    }

    fun close() = client.close()
}

@Serializable
data class GrokRequest(
    val model: String,
    val messages: List<GrokMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val temperature: Double = 0.7
)

@Serializable
data class GrokMessage(val role: String, val content: String)

@Serializable
data class GrokResponse(val choices: List<GrokChoice>? = null)

@Serializable
data class GrokChoice(
    val message: GrokMessage,
    val index: Int = 0,
    @SerialName("finish_reason") val finishReason: String? = null
)
