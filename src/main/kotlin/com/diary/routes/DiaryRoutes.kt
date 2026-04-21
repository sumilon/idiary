package com.diary.routes

import com.diary.models.*
import com.diary.services.DiaryRepository
import com.diary.services.GrokAIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.diaryRoutes(repo: DiaryRepository, grokService: GrokAIService) {
    authenticate("auth-jwt") {
        route("/diary") {

            // List entries with pagination and optional filters
            // Query params: limit, cursor, mood, keyword, startDate, endDate
            get {
                val userId    = call.userId()
                val limit     = call.parameters["limit"]?.toIntOrNull() ?: 20
                val cursor    = call.parameters["cursor"]
                val mood      = call.parameters["mood"]
                val keyword   = call.parameters["keyword"]
                val startDate = call.parameters["startDate"]?.toLongOrNull()
                val endDate   = call.parameters["endDate"]?.toLongOrNull()

                val paged = repo.getUserEntries(
                    userId    = userId,
                    limit     = limit,
                    cursor    = cursor,
                    mood      = mood,
                    keyword   = keyword,
                    startDate = startDate,
                    endDate   = endDate
                )
                val response = PagedResponse(
                    items      = paged.entries.map { it.toListItem() },
                    hasMore    = paged.hasMore,
                    nextCursor = paged.nextCursor
                )
                call.respond(ApiResponse(success = true, data = response))
            }

            // Mood analytics
            get("/analytics/mood") {
                val userId    = call.userId()
                val analytics = repo.getMoodAnalytics(userId)
                call.respond(ApiResponse(success = true, data = analytics))
            }

            // Journaling streak
            get("/analytics/streak") {
                val userId = call.userId()
                val streak = repo.getEntryStreak(userId)
                call.respond(ApiResponse(success = true, data = streak))
            }

            // Get a single entry
            get("/{id}") {
                val userId  = call.userId()
                val entryId = call.requireParam("id") ?: return@get
                val entry   = repo.getEntryById(entryId, userId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(success = false, message = "Entry not found")
                    )
                call.respond(ApiResponse(success = true, data = entry))
            }

            // Create a new entry
            post {
                val userId  = call.userId()
                val request = call.receive<CreateEntryRequest>()
                require(request.title.isNotBlank()) { "Title cannot be empty" }
                val now   = System.currentTimeMillis()
                val entry = DiaryEntry(
                    id        = UUID.randomUUID().toString(),
                    userId    = userId,
                    title     = request.title.trim(),
                    content   = request.content,
                    mood      = request.mood?.takeIf { it.isNotBlank() },
                    createdAt = now,
                    updatedAt = now
                )
                val created = repo.createEntry(entry)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = created))
            }

            // Update an existing entry
            put("/{id}") {
                val userId   = call.userId()
                val entryId  = call.requireParam("id") ?: return@put
                val existing = repo.getEntryById(entryId, userId)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(success = false, message = "Entry not found")
                    )
                val request = call.receive<UpdateEntryRequest>()
                require(request.title.isNotBlank()) { "Title cannot be empty" }
                val updated = existing.copy(
                    title     = request.title.trim(),
                    content   = request.content,
                    mood      = request.mood?.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis()
                )
                repo.updateEntry(updated)
                call.respond(ApiResponse(success = true, data = updated))
            }

            // Delete an entry
            delete("/{id}") {
                val userId  = call.userId()
                val entryId = call.requireParam("id") ?: return@delete
                val deleted = repo.deleteEntry(entryId, userId)
                if (deleted) {
                    call.respond(ApiResponse<Unit>(success = true, message = "Entry deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, message = "Entry not found"))
                }
            }

            // AI rewrite
            post("/ai-rewrite") {
                val request = call.receive<AiRewriteRequest>()
                require(request.content.isNotBlank()) { "Content cannot be empty" }
                val rewritten = grokService.rewriteContent(request.content, request.instruction)
                call.respond(ApiResponse(success = true, data = AiRewriteResponse(rewritten = rewritten)))
            }
        }
    }
}

// ── Private helpers ──

private suspend fun ApplicationCall.requireParam(name: String): String? {
    val value = parameters[name]
    if (value.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "Missing parameter: $name"))
        return null
    }
    return value
}

private fun DiaryEntry.toListItem() = DiaryListItem(
    id        = id,
    title     = title.ifBlank { "Untitled" },
    preview   = content.take(150),
    mood      = mood,
    createdAt = createdAt,
    updatedAt = updatedAt
)
