package com.diary.services

import com.diary.models.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * In-memory implementation of [DiaryRepository] for use in unit tests.
 * No Firebase dependency.
 */
class InMemoryDiaryRepository : DiaryRepository {

    val users   = mutableMapOf<String, User>()
    val entries = mutableMapOf<String, DiaryEntry>()

    override suspend fun createUser(user: User): User {
        users[user.id] = user
        return user
    }

    override suspend fun getUserById(userId: String): User? = users[userId]

    override suspend fun getUserByEmail(email: String): User? =
        users.values.firstOrNull { it.email == email }

    override suspend fun updatePasswordHash(userId: String, newHash: String) {
        users[userId]?.let { users[userId] = it.copy(passwordHash = newHash) }
    }

    override suspend fun updateUserName(userId: String, name: String) {
        users[userId]?.let { users[userId] = it.copy(name = name) }
    }

    override suspend fun createEntry(entry: DiaryEntry): DiaryEntry {
        entries[entry.id] = entry
        return entry
    }

    override suspend fun getEntryById(entryId: String, userId: String): DiaryEntry? =
        entries[entryId]?.takeIf { it.userId == userId }

    override suspend fun getUserEntries(
        userId: String,
        limit: Int,
        cursor: String?,
        mood: String?,
        keyword: String?,
        startDate: Long?,
        endDate: Long?
    ): PagedEntries {
        val pageSize = limit.coerceIn(1, 100)
        var list = entries.values.filter { it.userId == userId }
            .sortedByDescending { it.createdAt }

        if (!mood.isNullOrBlank())    list = list.filter { it.mood == mood }
        if (!keyword.isNullOrBlank()) list = list.filter {
            it.title.lowercase().contains(keyword.lowercase()) ||
            it.content.lowercase().contains(keyword.lowercase())
        }
        if (startDate != null) list = list.filter { it.createdAt >= startDate }
        if (endDate   != null) list = list.filter { it.createdAt <= endDate }

        if (!cursor.isNullOrBlank()) {
            val cursorTs = runCatching {
                String(Base64.getDecoder().decode(cursor)).toLong()
            }.getOrNull()
            if (cursorTs != null) list = list.filter { it.createdAt < cursorTs }
        }

        val hasMore = list.size > pageSize
        val page    = list.take(pageSize)
        val nextCursor = if (hasMore)
            Base64.getEncoder().encodeToString(page.last().createdAt.toString().toByteArray())
        else null

        return PagedEntries(page, hasMore, nextCursor)
    }

    override suspend fun updateEntry(entry: DiaryEntry): DiaryEntry {
        entries[entry.id] = entry
        return entry
    }

    override suspend fun deleteEntry(entryId: String, userId: String): Boolean {
        val entry = entries[entryId]?.takeIf { it.userId == userId } ?: return false
        entries.remove(entry.id)
        return true
    }

    override suspend fun getMoodAnalytics(userId: String): MoodAnalytics {
        val all     = entries.values.filter { it.userId == userId }
        val grouped = all.groupBy { it.mood }
        val counts  = grouped.filterKeys { it != null }
            .map { (mood, list) -> MoodCount(mood!!, list.size) }
            .sortedByDescending { it.count }
        return MoodAnalytics(counts, grouped[null]?.size ?: 0, all.size)
    }

    override suspend fun getEntryStreak(userId: String): EntryStreak {
        val dates = entries.values.filter { it.userId == userId }
            .map { Instant.ofEpochMilli(it.createdAt).atZone(ZoneOffset.UTC).toLocalDate() }
            .distinct().sorted().reversed()

        if (dates.isEmpty()) return EntryStreak(0, 0, 0)

        var longest = 1; var run = 1
        for (i in 1 until dates.size) {
            if (ChronoUnit.DAYS.between(dates[i], dates[i - 1]) == 1L) { run++; if (run > longest) longest = run }
            else run = 1
        }

        val today        = java.time.LocalDate.now(ZoneOffset.UTC)
        val daysSinceLast = ChronoUnit.DAYS.between(dates.first(), today)
        val current = if (daysSinceLast <= 1L) {
            var s = 1
            for (i in 1 until dates.size) {
                if (ChronoUnit.DAYS.between(dates[i], dates[i - 1]) == 1L) s++ else break
            }
            s
        } else 0

        return EntryStreak(current, longest, dates.size)
    }
}
