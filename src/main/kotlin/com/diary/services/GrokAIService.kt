package com.diary.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class GrokAIService(config: ApplicationConfig) {
    private val logger = LoggerFactory.getLogger(GrokAIService::class.java)
    private val apiKey = config.property("grok.apiKey").getString()
    private val baseUrl = config.property("grok.baseUrl").getString()
    private val model = config.property("grok.model").getString()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun rewriteContent(content: String, instruction: String): String {
        val systemPrompt = """You are a helpful diary writing assistant. Your job is to help users improve their diary entries.
Keep the personal voice and emotions of the writer intact. Make the text more readable, fix grammar, 
and improve sentence structure while maintaining the original meaning and sentiment.
Return ONLY the improved text, nothing else - no explanations, no preamble."""

        val userPrompt = when (instruction.lowercase()) {
            "improve" -> "Please improve this diary entry to make it more readable and well-written:\n\n$content"
            "grammar" -> "Please fix grammar and spelling errors in this diary entry:\n\n$content"
            "expand"  -> "Please expand and enrich this diary entry with more detail and expression:\n\n$content"
            "shorten" -> "Please make this diary entry more concise while keeping the key points:\n\n$content"
            "formal"  -> "Please rewrite this diary entry in a more formal, eloquent style:\n\n$content"
            else      -> "Please $instruction this diary entry:\n\n$content"
        }

        return try {
            val httpResponse = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(
                    GrokRequest(
                        model = model,
                        messages = listOf(
                            GrokMessage("system", systemPrompt),
                            GrokMessage("user", userPrompt)
                        ),
                        maxTokens = 2000,
                        temperature = 0.7
                    )
                )
            }

            val responseText = httpResponse.bodyAsText()

            if (!httpResponse.status.isSuccess()) {
                throw Exception("Groq API error: ${httpResponse.status} - $responseText")
            }

            val response: GrokResponse = json.decodeFromString(responseText)
            response.choices?.firstOrNull()?.message?.content
                ?: throw Exception("No response from AI")

        } catch (e: Exception) {
            logger.error("Groq API error: ${e.message}", e)
            throw Exception("AI service temporarily unavailable. Please try again.")
        }
    }

    fun close() = client.close()
}

@Serializable
data class GrokRequest(
    val model: String,
    val messages: List<GrokMessage>,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
    val temperature: Double = 0.7
) {
}

@Serializable
data class GrokMessage(
    val role: String,
    val content: String
)

@Serializable
data class GrokResponse(
//    val choices: List<GrokChoice>
    val choices: List<GrokChoice>? = null

)

@Serializable
data class GrokChoice(
    val message: GrokMessage,
    val index: Int = 0,
    @SerialName("finish_reason")
    val finishReason: String? = null
)
