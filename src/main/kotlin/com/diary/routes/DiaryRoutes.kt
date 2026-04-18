package com.diary.routes

import com.diary.models.*
import com.diary.services.FirebaseService
import com.diary.services.GrokAIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.diaryRoutes(grokService: GrokAIService) {
    authenticate("auth-jwt") {
        route("/diary") {

            // List all entries for the authenticated user
            get {
                val userId  = call.userId()
                val entries = FirebaseService.getUserEntries(userId)
                val items   = entries.map { it.toListItem() }
                call.respond(ApiResponse(success = true, data = items))
            }

            // Get a single entry
            get("/{id}") {
                val userId  = call.userId()
                val entryId = call.requireParam("id") ?: return@get
                val entry   = FirebaseService.getEntryById(entryId, userId)
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
                val now     = System.currentTimeMillis()
                val entry   = DiaryEntry(
                    id        = UUID.randomUUID().toString(),
                    userId    = userId,
                    title     = request.title,
                    content   = request.content,
                    mood      = request.mood,
                    createdAt = now,
                    updatedAt = now
                )
                val created = FirebaseService.createEntry(entry)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = created))
            }

            // Update an existing entry
            put("/{id}") {
                val userId  = call.userId()
                val entryId = call.requireParam("id") ?: return@put
                val existing = FirebaseService.getEntryById(entryId, userId)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Unit>(success = false, message = "Entry not found")
                    )
                val request = call.receive<UpdateEntryRequest>()
                val updated = existing.copy(
                    title     = request.title,
                    content   = request.content,
                    mood      = request.mood,
                    updatedAt = System.currentTimeMillis()
                )
                FirebaseService.updateEntry(updated)
                call.respond(ApiResponse(success = true, data = updated))
            }

            // Delete an entry
            delete("/{id}") {
                val userId  = call.userId()
                val entryId = call.requireParam("id") ?: return@delete
                val deleted = FirebaseService.deleteEntry(entryId, userId)
                if (deleted) {
                    call.respond(ApiResponse<Unit>(success = true, message = "Entry deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, message = "Entry not found"))
                }
            }

            // AI rewrite — returns enhanced text; the client decides whether to save it
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

/** Responds with 400 and returns null if [name] is missing from path parameters. */
private suspend fun ApplicationCall.requireParam(name: String): String? {
    val value = parameters[name]
    if (value.isNullOrBlank()) {
        respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "Missing parameter: $name"))
        return null
    }
    return value
}

/** Maps a [DiaryEntry] to its lightweight list representation. */
private fun DiaryEntry.toListItem() = DiaryListItem(
    id        = id,
    title     = title.ifBlank { "Untitled" },
    preview   = content.take(150),
    mood      = mood,
    createdAt = createdAt,
    updatedAt = updatedAt
)
