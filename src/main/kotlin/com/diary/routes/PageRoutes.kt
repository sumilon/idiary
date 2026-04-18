package com.diary.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Serves HTML template files from resources/templates/.
 *
 * For the entry page, two hidden-field values (entryId, isNewEntry) are
 * injected by simple string replacement so app.js knows whether it is
 * editing an existing entry or creating a new one.
 */
fun Route.pageRoutes() {

    get("/")          { call.respondRedirect("/login") }
    get("/login")     { call.respondTemplate("login.html") }
    get("/register")  { call.respondTemplate("register.html") }
    get("/dashboard") { call.respondTemplate("dashboard.html") }
    get("/profile")   { call.respondTemplate("profile.html") }

    get("/entry/new") {
        call.respondEntryPage(entryId = "", isNew = true)
    }

    get("/entry/{id}") {
        val id = call.parameters["id"] ?: ""
        call.respondEntryPage(entryId = id, isNew = false)
    }
}

// ── Helpers ──

private suspend fun ApplicationCall.respondTemplate(filename: String) {
    respondText(loadTemplate(filename), ContentType.Text.Html)
}

/**
 * Serves entry.html with the two hidden fields populated via string replacement.
 * This avoids a full template-engine dependency while keeping the HTML clean.
 */
private suspend fun ApplicationCall.respondEntryPage(entryId: String, isNew: Boolean) {
    var html = loadTemplate("entry.html")

    html = html
        .replace(
            """<input type="hidden" id="entryId"     value="">""",
            """<input type="hidden" id="entryId"     value="$entryId">"""
        )
        .replace(
            """<input type="hidden" id="isNewEntry"  value="true">""",
            """<input type="hidden" id="isNewEntry"  value="${if (isNew) "true" else "false"}">"""
        )

    respondText(html, ContentType.Text.Html)
}

private fun loadTemplate(filename: String): String {
    val stream = object {}.javaClass
        .classLoader
        .getResourceAsStream("templates/$filename")
        ?: error("Template not found: templates/$filename")
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
