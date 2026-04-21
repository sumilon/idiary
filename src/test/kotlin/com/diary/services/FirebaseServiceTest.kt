package com.diary.services

import com.diary.models.*
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.*

/**
 * Unit tests for repository behaviour (backed by [InMemoryDiaryRepository]).
 * No Firebase dependency needed.
 */
class FirebaseServiceTest {

    private lateinit var repo: InMemoryDiaryRepository

    private fun user(id: String = UUID.randomUUID().toString(), email: String = "$id@test.com") =
        User(id = id, email = email, name = "Test User", passwordHash = "hash")

    private fun entry(
        userId: String,
        id: String = UUID.randomUUID().toString(),
        mood: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ) = DiaryEntry(id = id, userId = userId, title = "Title $id", content = "Content $id",
                   mood = mood, createdAt = createdAt, updatedAt = createdAt)

    @BeforeTest
    fun setup() { repo = InMemoryDiaryRepository() }

    // ── User operations ──

    @Test
    fun `createUser and getUserById round-trip`() = runBlocking<Unit> {
        val u = user()
        repo.createUser(u)
        assertEquals(u, repo.getUserById(u.id))
    }

    @Test
    fun `getUserByEmail returns correct user`() = runBlocking<Unit> {
        val u = user(email = "find@me.com")
        repo.createUser(u)
        assertEquals(u, repo.getUserByEmail("find@me.com"))
        assertNull(repo.getUserByEmail("nobody@me.com"))
    }

    @Test
    fun `updatePasswordHash changes hash`() = runBlocking<Unit> {
        val u = user()
        repo.createUser(u)
        repo.updatePasswordHash(u.id, "newhash")
        assertEquals("newhash", repo.getUserById(u.id)?.passwordHash)
    }

    @Test
    fun `updateUserName changes name`() = runBlocking<Unit> {
        val u = user()
        repo.createUser(u)
        repo.updateUserName(u.id, "Renamed")
        assertEquals("Renamed", repo.getUserById(u.id)?.name)
    }

    // ── Diary entry CRUD ──

    @Test
    fun `createEntry and getEntryById round-trip`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        val e = entry(u.id)
        repo.createEntry(e)
        assertEquals(e, repo.getEntryById(e.id, u.id))
    }

    @Test
    fun `getEntryById returns null for wrong user`() = runBlocking<Unit> {
        val u1 = user(); val u2 = user()
        repo.createUser(u1); repo.createUser(u2)
        val e = entry(u1.id)
        repo.createEntry(e)
        assertNull(repo.getEntryById(e.id, u2.id))
    }

    @Test
    fun `updateEntry persists changes`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        val e = entry(u.id); repo.createEntry(e)
        val updated = e.copy(title = "Updated Title")
        repo.updateEntry(updated)
        assertEquals("Updated Title", repo.getEntryById(e.id, u.id)?.title)
    }

    @Test
    fun `deleteEntry removes entry`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        val e = entry(u.id); repo.createEntry(e)
        assertTrue(repo.deleteEntry(e.id, u.id))
        assertNull(repo.getEntryById(e.id, u.id))
    }

    @Test
    fun `deleteEntry returns false for wrong user`() = runBlocking<Unit> {
        val u1 = user(); val u2 = user()
        repo.createUser(u1); repo.createUser(u2)
        val e = entry(u1.id); repo.createEntry(e)
        assertFalse(repo.deleteEntry(e.id, u2.id))
        assertNotNull(repo.getEntryById(e.id, u1.id))
    }

    // ── Pagination ──

    @Test
    fun `getUserEntries returns paginated results`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        repeat(5) { i -> repo.createEntry(entry(u.id, createdAt = 1000L * (i + 1))) }

        val page1 = repo.getUserEntries(u.id, limit = 3)
        assertEquals(3, page1.entries.size)
        assertTrue(page1.hasMore)
        assertNotNull(page1.nextCursor)

        val page2 = repo.getUserEntries(u.id, limit = 3, cursor = page1.nextCursor)
        assertEquals(2, page2.entries.size)
        assertFalse(page2.hasMore)
        assertNull(page2.nextCursor)
    }

    // ── Filters ──

    @Test
    fun `getUserEntries filters by mood`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        repo.createEntry(entry(u.id, mood = "happy"))
        repo.createEntry(entry(u.id, mood = "sad"))
        repo.createEntry(entry(u.id, mood = "happy"))

        val result = repo.getUserEntries(u.id, mood = "happy")
        assertEquals(2, result.entries.size)
        assertTrue(result.entries.all { it.mood == "happy" })
    }


    @Test
    fun `getUserEntries filters by keyword`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        val e1 = entry(u.id).copy(title = "Vacation in Paris"); repo.createEntry(e1)
        val e2 = entry(u.id).copy(title = "Regular day"); repo.createEntry(e2)

        val result = repo.getUserEntries(u.id, keyword = "paris")
        assertEquals(1, result.entries.size)
        assertEquals(e1.id, result.entries.first().id)
    }

    // ── Analytics ──

    @Test
    fun `getMoodAnalytics returns correct counts`() = runBlocking<Unit> {
        val u = user(); repo.createUser(u)
        repeat(3) { repo.createEntry(entry(u.id, mood = "happy")) }
        repeat(1) { repo.createEntry(entry(u.id, mood = "sad")) }
        repeat(2) { repo.createEntry(entry(u.id, mood = null)) }

        val analytics = repo.getMoodAnalytics(u.id)
        assertEquals(6, analytics.totalEntries)
        assertEquals(2, analytics.noMoodCount)
        assertEquals(3, analytics.moodCounts.first { it.mood == "happy" }.count)
    }

    @Test
    fun `getEntryStreak calculates active streak correctly`() = runBlocking<Unit> {
        val u   = user(); repo.createUser(u)
        val today = System.currentTimeMillis()
        val day   = 86_400_000L

        repo.createEntry(entry(u.id, createdAt = today))
        repo.createEntry(entry(u.id, createdAt = today - day))
        repo.createEntry(entry(u.id, createdAt = today - 2 * day))

        val streak = repo.getEntryStreak(u.id)
        assertEquals(3, streak.currentStreak)
        assertEquals(3, streak.longestStreak)
        assertEquals(3, streak.totalDaysJournaled)
    }

    @Test
    fun `getEntryStreak returns zero current streak when inactive`() = runBlocking<Unit> {
        val u   = user(); repo.createUser(u)
        val day = 86_400_000L

        repo.createEntry(entry(u.id, createdAt = System.currentTimeMillis() - 5 * day))
        repo.createEntry(entry(u.id, createdAt = System.currentTimeMillis() - 6 * day))

        val streak = repo.getEntryStreak(u.id)
        assertEquals(0, streak.currentStreak)
    }
}
