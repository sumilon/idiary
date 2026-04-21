package com.diary.services

import com.diary.models.*
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Query
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class FirebaseService(config: ApplicationConfig) : DiaryRepository {

    private val db: Firestore

    init {
        val projectId  = config.property("firebase.projectId").getString()
        val databaseId = config.propertyOrNull("firebase.databaseId")?.getString() ?: "diaryapp"
        val credentials = resolveCredentials()

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build()
            )
        }

        db = FirestoreOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(credentials)
            .setDatabaseId(databaseId)
            .build()
            .service
    }

    private fun resolveCredentials(): GoogleCredentials {
        val json = System.getenv("FIREBASE_CREDENTIALS_JSON")
        if (!json.isNullOrBlank()) return GoogleCredentials.fromStream(ByteArrayInputStream(json.toByteArray()))

        val path = System.getenv("FIREBASE_CREDENTIALS_PATH")
        if (!path.isNullOrBlank()) return GoogleCredentials.fromStream(FileInputStream(path))

        return GoogleCredentials.getApplicationDefault()
    }

    // ── Users ──

    override suspend fun createUser(user: User): User = withContext(Dispatchers.IO) {
        db.collection("users").document(user.id).set(
            mapOf(
                "id"           to user.id,
                "email"        to user.email,
                "name"         to user.name,
                "passwordHash" to user.passwordHash,
                "createdAt"    to user.createdAt
            )
        ).get()
        user
    }

    override suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        db.collection("users").document(userId).get().get().toUser()
    }

    override suspend fun getUserByEmail(email: String): User? = withContext(Dispatchers.IO) {
        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get().get()
            .documents.firstOrNull()?.toUser()
    }

    override suspend fun updatePasswordHash(userId: String, newHash: String): Unit = withContext(Dispatchers.IO) {
        db.collection("users").document(userId).update("passwordHash", newHash).get()
    }

    override suspend fun updateUserName(userId: String, name: String): Unit = withContext(Dispatchers.IO) {
        db.collection("users").document(userId).update("name", name).get()
    }

    // ── Diary entries ──

    override suspend fun createEntry(entry: DiaryEntry): DiaryEntry = withContext(Dispatchers.IO) {
        db.collection("diaries").document(entry.id).set(entry.toMap()).get()
        entry
    }

    override suspend fun getEntryById(entryId: String, userId: String): DiaryEntry? = withContext(Dispatchers.IO) {
        db.collection("diaries").document(entryId).get().get()
            .toDiaryEntry()?.takeIf { it.userId == userId }
    }

    override suspend fun getUserEntries(
        userId: String,
        limit: Int,
        cursor: String?,
        mood: String?,
        keyword: String?,
        startDate: Long?,
        endDate: Long?
    ): PagedEntries = withContext(Dispatchers.IO) {
        val pageSize = limit.coerceIn(1, 100)

        var query: Query = db.collection("diaries").whereEqualTo("userId", userId)

        if (!mood.isNullOrBlank()) query = query.whereEqualTo("mood", mood)

        query = query.orderBy("createdAt", Query.Direction.DESCENDING)

        // Cursor-based pagination: skip entries older than the cursor's createdAt
        if (!cursor.isNullOrBlank()) {
            runCatching {
                val cursorTs = String(Base64.getDecoder().decode(cursor)).toLong()
                query = query.whereLessThan("createdAt", cursorTs)
            }
        }

        // Fetch pageSize + 1 to detect hasMore
        val docs = query.limit(pageSize + 1).get().get().documents
        val hasMore = docs.size > pageSize
        val pageDocs = if (hasMore) docs.dropLast(1) else docs

        var entries = pageDocs.mapNotNull { it.toDiaryEntry() }

        // In-memory filters (keyword, date range)
        if (!keyword.isNullOrBlank()) {
            val kw = keyword.lowercase()
            entries = entries.filter {
                it.title.lowercase().contains(kw) || it.content.lowercase().contains(kw)
            }
        }
        if (startDate != null) entries = entries.filter { it.createdAt >= startDate }
        if (endDate   != null) entries = entries.filter { it.createdAt <= endDate }

        val nextCursor = if (hasMore) {
            Base64.getEncoder().encodeToString(entries.last().createdAt.toString().toByteArray())
        } else null

        PagedEntries(entries, hasMore, nextCursor)
    }

    override suspend fun updateEntry(entry: DiaryEntry): DiaryEntry = withContext(Dispatchers.IO) {
        db.collection("diaries").document(entry.id).set(entry.toMap()).get()
        entry
    }

    override suspend fun deleteEntry(entryId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        val entry = db.collection("diaries").document(entryId).get().get().toDiaryEntry()
        if (entry?.userId == userId) {
            db.collection("diaries").document(entryId).delete().get()
            true
        } else false
    }

    // ── Analytics ──

    override suspend fun getMoodAnalytics(userId: String): MoodAnalytics = withContext(Dispatchers.IO) {
        val docs = db.collection("diaries")
            .whereEqualTo("userId", userId)
            .get().get()
            .documents.mapNotNull { it.toDiaryEntry() }

        val grouped = docs.groupBy { it.mood }
        val moodCounts = grouped
            .filterKeys { it != null }
            .map { (mood, list) -> MoodCount(mood!!, list.size) }
            .sortedByDescending { it.count }

        MoodAnalytics(
            moodCounts    = moodCounts,
            noMoodCount   = grouped[null]?.size ?: 0,
            totalEntries  = docs.size
        )
    }

    override suspend fun getEntryStreak(userId: String): EntryStreak = withContext(Dispatchers.IO) {
        val dates = db.collection("diaries")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().get()
            .documents
            .mapNotNull { it.getLong("createdAt") }
            .map { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            .distinct()
            .sorted()
            .reversed()

        if (dates.isEmpty()) return@withContext EntryStreak(0, 0, 0)

        var longest = 1
        var run = 1

        for (i in 1 until dates.size) {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(dates[i], dates[i - 1])
            if (daysBetween == 1L) {
                run++
                if (run > longest) longest = run
            } else {
                run = 1
            }
        }

        // Check if the streak is still active (today or yesterday)
        val today = java.time.LocalDate.now(ZoneOffset.UTC)
        val daysSinceLast = java.time.temporal.ChronoUnit.DAYS.between(dates.first(), today)
        val currentStreak = if (daysSinceLast <= 1L) {
            // Recalculate current streak from the front
            var streak = 1
            for (i in 1 until dates.size) {
                val diff = java.time.temporal.ChronoUnit.DAYS.between(dates[i], dates[i - 1])
                if (diff == 1L) streak++ else break
            }
            streak
        } else 0

        EntryStreak(
            currentStreak     = currentStreak,
            longestStreak     = longest,
            totalDaysJournaled = dates.size
        )
    }

    // ── Mappers ──

    private fun DocumentSnapshot.toUser(): User? {
        if (!exists()) return null
        return User(
            id           = getString("id") ?: id,
            email        = getString("email") ?: return null,
            name         = getString("name") ?: "",
            passwordHash = getString("passwordHash") ?: "",
            createdAt    = getLong("createdAt") ?: System.currentTimeMillis()
        )
    }

    private fun DocumentSnapshot.toDiaryEntry(): DiaryEntry? {
        if (!exists()) return null
        return DiaryEntry(
            id        = getString("id") ?: id,
            userId    = getString("userId") ?: return null,
            title     = getString("title") ?: "",
            content   = getString("content") ?: "",
            mood      = getString("mood"),
            createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    private fun DiaryEntry.toMap(): Map<String, Any?> = mapOf(
        "id"        to id,
        "userId"    to userId,
        "title"     to title,
        "content"   to content,
        "mood"      to mood,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}
