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

object FirebaseService {

    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)
    private lateinit var db: Firestore

    fun initialize(config: ApplicationConfig) {
        try {
            val projectId       = config.property("firebase.projectId").getString()
            val credentialsJson = buildCredentialsJson()
            val credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH")

            val credentials = when {
                credentialsJson.isNotBlank() ->
                    GoogleCredentials.fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
                !credentialsPath.isNullOrBlank() ->
                    GoogleCredentials.fromStream(java.io.FileInputStream(credentialsPath))
                else ->
                    GoogleCredentials.getApplicationDefault()
            }

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
                .setDatabaseId("diaryapp")
                .build()
                .service

            logger.info("Firebase initialized for project: $projectId")
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase: ${e.message}", e)
            throw e
        }
    }

    // ── User operations ──

    suspend fun createUser(user: User): User = withContext(Dispatchers.IO) {
        db.collection("users").document(user.id).set(user.toMap()).get()
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

    // ── Diary operations ──

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

    private fun User.toMap(): Map<String, Any?> = mapOf(
        "id"           to id,
        "email"        to email,
        "name"         to name,
        "passwordHash" to passwordHash,
        "createdAt"    to createdAt
    )

    private fun DiaryEntry.toMap(): Map<String, Any?> = mapOf(
        "id"        to id,
        "userId"    to userId,
        "title"     to title,
        "content"   to content,
        "mood"      to mood,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    // ── Credentials (loaded from embedded config; prefer env var in production) ──

    private fun buildCredentialsJson(): String = """
        {
          "type": "service_account",
          "project_id": "project-875e5c46-ac36-45f9-b83",
          "private_key_id": "5cdf73620f20986faa833fae89d5c00d3ff922d6",
          "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDoetq+bKPV76oV\njYhBkkYtDOlW5b1fdnE/xNvxoUYGZ3qs2F8CKohgIs0kQ7IboMmIsdvBPlDHOcXO\nYNBt3Z1GSsa62cJbzsFJCI2TYKGMpJiCK0fa/9HZYWvVA0JhPo1/tNoU+Ilv/R6u\nluJKzSet/f5Ze5w7eYexMZHVuCencGrmELXlub7+buDT2W/cHksUjHw5fgyF4ujg\ne4oEGwtjTyYvYOPLJqF1hCF5LmEpuVfLpMSPVDL+szkUmCUFwtHtPa8MUTExniHt\nVWpb1pLLBAGmmeh2JVcHkbO0PwvMH6aeYjJqQpnHdUv239LTAoNxQTfHWy4NBaCb\nKsF0hIh3AgMBAAECggEAGs1h1LG/vcAbctAAv9aLrVFKFuZLtpV4Karvfzm2/LFc\nwk/7vCCH3SrbTcKu5mKGJEf6bEVb7ha/XbNptDuS5fIPBqcTsklG8r2VXO1iEqM9\n9fE5dTUoeLsI7I5V+Sd2DB10mxWq+rSgSe6ZTkMSv7YtvHFKKiKGaKusrYrFVOyi\nZMmkPosF6kboBan5I2zPaurfeGKZYUSLtnA0dJeMBR64wOWRM759quD5Nb6645S0\nXunR04xxJnYVpUrTJkEDa5yPl1d//HnmL7IMvcuAdOMsyG1N52CT0b2fKr+WElo+\nlabmziltp5wXgEZsTPpVo4TA5TMINpJrfpG1RAyrKQKBgQD7aRCBmbbkdyHhKi24\nI2Br2gmgsp9H1Bn1Sv/tWNz+Z+WanOCpeVGDuTemWUiPNqGfTQiXZVIVsK6Z+KcN\nYEIbrqbl+wrO9nSUOAEZ9o0A9rSNEAegC+vGBZ1fcO//wC7FwLJVwdWiuf5JXcBQ\nhsDcx6ka15R4oF4qfctFZfAx5QKBgQDsuVISs9PPYeyBGyQpvkE/obSYIIiJInqB\nHslF1iutY9swcWj4YAFoi6JM+ctCAkkWkyiaWlNz0dgqNxxBEe403MXRXzBWjdC6\nDQHxmXfvIHSUGbmhjr6XWvg9b8UnbN2/oaHAaNr5bzBRSxK6MHlx4SiPWJBe7Rn1\nDD6XRbUbKwKBgHFBRDlN2LEU5cM8L/panW4Ye+vTa6N87fCtR5tRQ8SrYyiCcUaH\nK2xufJ5IbEJvtuE/X5ZsA01YGV+tPvvsl/tGc1L5A0Z2ic/XZs+eXKjJek5toTG9\nDQpYrys2gjcxCSD2jJ/LQQUcSmwceq0L90e9/fTklrK8c48que5aXJjtAoGBAOl2\nU6UySQDMOK2TkPTCOCODXVJzM3Tb1imlrSb89BKK2s9J6haayIjMvYJhEL7G9kIX\nXAHud0NQf3oS5ACgqGbmHQpCcK/MKUB0N6iIjKn/PjoZrOPSndEz3ILqymv5iDDI\nPXg/cUZzDBoAijWqPPu87X2CANA90k3Eh641B0oFAoGAaQgxlVnSXL31XG3eHgJL\n1H2YpFJ6zHkcBQPagytrzpPeJQZPcwnLpVraoF6NNf0v0zRDvfh7pC5pB6riA3nh\n1BV5t7ReuIlhzy4dfHewj/Xj9nqil1nkYB2mSQugAOFi+uTnkSPVh2DiJxR6dZjR\ntJ1EogFIV2D+Moe3320IvMo=\n-----END PRIVATE KEY-----\n",
          "client_email": "firebase-adminsdk-fbsvc@project-875e5c46-ac36-45f9-b83.iam.gserviceaccount.com",
          "client_id": "105968775882350647820",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token",
          "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
          "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/firebase-adminsdk-fbsvc%40project-875e5c46-ac36-45f9-b83.iam.gserviceaccount.com",
          "universe_domain": "googleapis.com"
        }
    """.trimIndent()
}
