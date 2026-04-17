package com.diary.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Serves the standalone HTML template files from resources/templates/.
 *
 * For the entry page we inject two tiny hidden-field values
 * (entryId and isNewEntry) into the HTML so app.js knows
 * whether it is editing an existing entry or creating a new one.
 * Everything else (CSS, JS) is a normal static file.
 */
fun Route.pageRoutes() {

    // Root → login
    get("/") {
        call.respondRedirect("/login")
    }

    get("/login") {
        call.respondHtmlTemplate("login.html")
    }

    get("/register") {
        call.respondHtmlTemplate("register.html")
    }

    get("/dashboard") {
        call.respondHtmlTemplate("dashboard.html")
    }

    get("/entry/new") {
        call.respondEntryPage(entryId = "", isNew = true)
    }

    get("/entry/{id}") {
        val id = call.parameters["id"] ?: ""
        call.respondEntryPage(entryId = id, isNew = false)
    }
}

// ── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Reads a template file from resources/templates/ and sends it as HTML.
 */
private suspend fun ApplicationCall.respondHtmlTemplate(filename: String) {
    val content = loadTemplate(filename)
    respondText(content, ContentType.Text.Html)
}

/**
 * Serves entry.html with the two hidden fields populated.
 * Simple string replacement keeps the HTML file clean without a full
 * template engine dependency.
 */
private suspend fun ApplicationCall.respondEntryPage(entryId: String, isNew: Boolean) {
    var html = loadTemplate("entry.html")

    html = html
        .replace(
            """<input type="hidden" id="entryId"           value="">""",
            """<input type="hidden" id="entryId"           value="$entryId">"""
        )
        .replace(
            """<input type="hidden" id="isNewEntry"        value="true">""",
            """<input type="hidden" id="isNewEntry"        value="${if (isNew) "true" else "false"}">"""
        )

    respondText(html, ContentType.Text.Html)
}

/**
 * Loads a template file from the classpath (resources/templates/).
 */
private fun loadTemplate(filename: String): String {
    val resource = object {}.javaClass
        .classLoader
        .getResourceAsStream("templates/$filename")
        ?: error("Template not found: templates/$filename")

    return resource.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
