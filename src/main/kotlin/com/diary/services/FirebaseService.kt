package com.diary.services

import com.diary.models.DiaryEntry
import com.diary.models.User
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
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.FileInputStream

object FirebaseService {
    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)
    private lateinit var db: Firestore

    fun initialize(config: ApplicationConfig) {
        try {
            val projectId = config.property("firebase.projectId").getString()
            val databaseId = config.propertyOrNull("firebase.databaseId")?.getString() ?: "diaryapp"

            val credentials = resolveCredentials()

            val firebaseOptions = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(firebaseOptions)
            }

            db = FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .setDatabaseId(databaseId)
                .build()
                .service

            logger.info("Firebase initialized for project: $projectId, database: $databaseId")
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase: ${e.message}", e)
            throw e
        }
    }

    /**
     * Credential resolution order:
     *  1. FIREBASE_CREDENTIALS_JSON  — JSON string injected as a Cloud Run secret env var
     *  2. FIREBASE_CREDENTIALS_PATH  — path to a credentials file (local dev / mounted secret)
     *  3. Application Default Credentials — automatic on Cloud Run when IAM role is granted
     */
    private fun resolveCredentials(): GoogleCredentials {
        val credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON")
        if (!credentialsJson.isNullOrBlank()) {
            logger.info("Using Firebase credentials from FIREBASE_CREDENTIALS_JSON")
            return GoogleCredentials.fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
        }

        val credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH")
        if (!credentialsPath.isNullOrBlank()) {
            logger.info("Using Firebase credentials from file: $credentialsPath")
            return GoogleCredentials.fromStream(FileInputStream(credentialsPath))
        }

        logger.info("Using Application Default Credentials")
        return GoogleCredentials.getApplicationDefault()
    }

    // ---- User Operations ----

    suspend fun createUser(user: User): User = withContext(Dispatchers.IO) {
        db.collection("users").document(user.id).set(mapOf(
            "id"           to user.id,
            "email"        to user.email,
            "name"         to user.name,
            "passwordHash" to user.passwordHash,
            "createdAt"    to user.createdAt
        )).get()
        user
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        db.collection("users").document(userId).get().get().toUser()
    }

    suspend fun getUserByEmail(email: String): User? = withContext(Dispatchers.IO) {
        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get().get()
            .documents.firstOrNull()?.toUser()
    }

    suspend fun updatePasswordHash(userId: String, newHash: String): Unit = withContext(Dispatchers.IO) {
        db.collection("users").document(userId)
            .update("passwordHash", newHash)
            .get()
    }

    // ---- Diary Operations ----

    suspend fun createEntry(entry: DiaryEntry): DiaryEntry = withContext(Dispatchers.IO) {
        db.collection("diaries").document(entry.id).set(entry.toMap()).get()
        entry
    }

    suspend fun getEntryById(entryId: String, userId: String): DiaryEntry? = withContext(Dispatchers.IO) {
        val entry = db.collection("diaries").document(entryId).get().get().toDiaryEntry()
        entry?.takeIf { it.userId == userId }
    }

    suspend fun getUserEntries(userId: String, limit: Int = 50): List<DiaryEntry> = withContext(Dispatchers.IO) {
        db.collection("diaries")
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get().get()
            .documents.mapNotNull { it.toDiaryEntry() }
    }

    suspend fun updateEntry(entry: DiaryEntry): DiaryEntry = withContext(Dispatchers.IO) {
        db.collection("diaries").document(entry.id).set(entry.toMap()).get()
        entry
    }

    suspend fun deleteEntry(entryId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        val entry = db.collection("diaries").document(entryId).get().get().toDiaryEntry()
        if (entry?.userId == userId) {
            db.collection("diaries").document(entryId).delete().get()
            true
        } else {
            false
        }
    }

    // ---- Mappers ----

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
