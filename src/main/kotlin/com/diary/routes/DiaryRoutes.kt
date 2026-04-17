package com.diary.routes

import com.diary.models.*
import com.diary.services.FirebaseService
import com.diary.services.GrokAIService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.diaryRoutes(grokService: GrokAIService) {
    authenticate("auth-jwt") {
        route("/diary") {

            // Get all entries for user
            get {
                val userId = call.userId()
                val entries = FirebaseService.getUserEntries(userId)
                val items = entries.map { entry ->
                    DiaryListItem(
                        id = entry.id,
                        title = entry.title.ifBlank { "Untitled" },
                        preview = entry.content.take(150),  // single content field, no branching needed
                        mood = entry.mood,
                        createdAt = entry.createdAt,
                        updatedAt = entry.updatedAt
                    )
                }
                call.respond(ApiResponse(success = true, data = items))
            }

            // Get single entry
            get("/{id}") {
                val userId = call.userId()
                val entryId = call.parameters["id"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "Missing entry ID")
                )
                val entry = FirebaseService.getEntryById(entryId, userId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, message = "Entry not found")
                    )
                call.respond(ApiResponse(success = true, data = entry))
            }

            // Create entry
            post {
                val userId = call.userId()
                val request = call.receive<CreateEntryRequest>()
                val entry = DiaryEntry(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = request.title,
                    content = request.content,
                    mood = request.mood,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val created = FirebaseService.createEntry(entry)
                call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = created))
            }

            // Update entry — client sends whichever content to persist (original or AI-rewritten)
            put("/{id}") {
                val userId = call.userId()
                val entryId = call.parameters["id"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "Missing entry ID")
                )
                val existing = FirebaseService.getEntryById(entryId, userId)
                    ?: return@put call.respond(
                        HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, message = "Entry not found")
                    )
                val request = call.receive<UpdateEntryRequest>()
                val updated = existing.copy(
                    title = request.title,
                    content = request.content,
                    mood = request.mood,
                    updatedAt = System.currentTimeMillis()
                )
                FirebaseService.updateEntry(updated)
                call.respond(ApiResponse(success = true, data = updated))
            }

            // Delete entry
            delete("/{id}") {
                val userId = call.userId()
                val entryId = call.parameters["id"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, message = "Missing entry ID")
                )
                val deleted = FirebaseService.deleteEntry(entryId, userId)
                if (deleted) {
                    call.respond(ApiResponse<Unit>(success = true, message = "Entry deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, message = "Entry not found"))
                }
            }

            // AI Rewrite — returns rewritten text only; client decides whether to save it via PUT
            post("/ai-rewrite") {
                val request = call.receive<AiRewriteRequest>()
                if (request.content.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<Unit>(success = false, message = "Content cannot be empty")
                    )
                }
                val rewritten = grokService.rewriteContent(request.content, request.instruction)
                call.respond(
                    ApiResponse(
                        success = true,
                        data = AiRewriteResponse(rewritten = rewritten)
                    )
                )
            }
        }
    }
}

fun ApplicationCall.userId(): String {
    val principal = principal<JWTPrincipal>()
        ?: throw SecurityException("Unauthorized")
    return principal.payload.getClaim("userId").asString()
        ?: throw SecurityException("Invalid token")
}
