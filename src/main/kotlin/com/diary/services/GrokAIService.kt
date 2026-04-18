package com.diary.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class GrokAIService(config: ApplicationConfig) {

    private val logger  = LoggerFactory.getLogger(GrokAIService::class.java)
    private val apiKey  = config.property("grok.apiKey").getString()
    private val baseUrl = config.property("grok.baseUrl").getString()
    private val model   = config.property("grok.model").getString()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging)            { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun rewriteContent(content: String, instruction: String): String {
        val systemPrompt = """
            You are a helpful diary writing assistant. Help users improve their diary entries while
            preserving their personal voice and emotions. Return ONLY the improved text — no
            explanations, no preamble.
        """.trimIndent()

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
                setBody(
                    GrokRequest(
                        model    = model,
                        messages = listOf(
                            GrokMessage("system", systemPrompt),
                            GrokMessage("user",   userPrompt)
                        )
                    )
                )
            }

            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw Exception("Groq API error: ${response.status} – $body")
            }

            json.decodeFromString<GrokResponse>(body)
                .choices?.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")

        } catch (e: Exception) {
            logger.error("Groq API error: ${e.message}", e)
            throw Exception("AI service temporarily unavailable. Please try again.")
        }
    }

    fun close() = client.close()
}

// ── Groq API data classes ──

@Serializable
data class GrokRequest(
    val model: String,
    val messages: List<GrokMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val temperature: Double = 0.7
)

@Serializable
data class GrokMessage(
    val role: String,
    val content: String
)

@Serializable
data class GrokResponse(
    val choices: List<GrokChoice>? = null
)

@Serializable
data class GrokChoice(
    val message: GrokMessage,
    val index: Int = 0,
    @SerialName("finish_reason") val finishReason: String? = null
)
