package com.diary.services

import com.diary.models.*

/**
 * Abstraction over the persistence layer.
 * Implement this interface to swap Firebase for any other backend (e.g. in tests).
 */
interface DiaryRepository {

    // ── Users ──

    suspend fun createUser(user: User): User
    suspend fun getUserById(userId: String): User?
    suspend fun getUserByEmail(email: String): User?
    suspend fun updatePasswordHash(userId: String, newHash: String)
    suspend fun updateUserName(userId: String, name: String)

    // ── Diary entries ──

    suspend fun createEntry(entry: DiaryEntry): DiaryEntry
    suspend fun getEntryById(entryId: String, userId: String): DiaryEntry?

    /**
     * Returns a page of diary entries ordered by [createdAt] descending.
     *
     * @param cursor  Opaque cursor from the previous page (base-64 of the last entry's createdAt).
     *                `null` fetches the first page.
     * @param limit   Max entries to return (server-side cap: 100).
     * @param mood    If set, only return entries with this mood.
     * @param keyword If set, filter entries whose title or content contains this string (case-insensitive, in-memory).
     * @param startDate / endDate  Unix-ms date range filter applied in-memory.
     */
    suspend fun getUserEntries(
        userId: String,
        limit: Int = 20,
        cursor: String? = null,
        mood: String? = null,
        keyword: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): PagedEntries

    suspend fun updateEntry(entry: DiaryEntry): DiaryEntry
    suspend fun deleteEntry(entryId: String, userId: String): Boolean

    // ── Analytics ──

    suspend fun getMoodAnalytics(userId: String): MoodAnalytics
    suspend fun getEntryStreak(userId: String): EntryStreak
}

/** Lightweight pagination wrapper returned by [DiaryRepository.getUserEntries]. */
data class PagedEntries(
    val entries: List<DiaryEntry>,
    val hasMore: Boolean,
    val nextCursor: String?
)
