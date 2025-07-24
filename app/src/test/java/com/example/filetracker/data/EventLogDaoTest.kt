package com.example.filetracker.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class EventLogDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var eventLogDao: EventLogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        eventLogDao = database.eventLogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_insertsEntrySuccessfully() = runTest {
        // Arrange
        val timestamp = 1000L
        val message = "Test message"
        val entry = EventLog(timestamp = timestamp, message = message)

        // Act
        eventLogDao.insert(entry)
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(1, result.size)
        assertEquals(timestamp, result[0].timestamp)
        assertEquals(message, result[0].message)
    }

    @Test
    fun getRecent_returnsAtMost100Entries() = runTest {
        // Arrange
        for (i in 1..105) {
            val entry = EventLog(timestamp = i.toLong(), message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Act
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(100, result.size)
        assertEquals(105L, result[0].timestamp) // Most recent first
        assertEquals(6L, result[99].timestamp)  // Oldest in the list
    }

    @Test
    fun getRecentLimited_returnsSpecifiedNumberOfEntries() = runTest {
        // Arrange
        for (i in 1..50) {
            val entry = EventLog(timestamp = i.toLong(), message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Act
        val result = getLiveDataValue(eventLogDao.getRecentLimited(10))

        // Assert
        assertEquals(10, result.size)
        assertEquals(50L, result[0].timestamp) // Most recent first
        assertEquals(41L, result[9].timestamp) // Tenth most recent
    }

    @Test
    fun getRecentLimited_withZeroLimit_returnsEmptyList() = runTest {
        // Arrange
        for (i in 1..5) {
            val entry = EventLog(timestamp = i.toLong(), message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Act
        val result = getLiveDataValue(eventLogDao.getRecentLimited(0))

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun getRecent_fromEmptyTable_returnsEmptyList() = runTest {
        // Act
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun clear_removesAllEntries() = runTest {
        // Arrange
        for (i in 1..10) {
            val entry = EventLog(timestamp = i.toLong(), message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Act
        eventLogDao.clear()
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(0, result.size)
    }

    @Test
    fun getCount_returnsCorrectCount() = runTest {
        // Arrange & Act - Initial count
        var count = getLiveDataValue(eventLogDao.getCount())
        assertEquals(0, count)

        // Insert 3 entries
        for (i in 1..3) {
            val entry = EventLog(timestamp = i.toLong(), message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Check count after insert
        count = getLiveDataValue(eventLogDao.getCount())
        assertEquals(3, count)

        // Clear and check count
        eventLogDao.clear()
        count = getLiveDataValue(eventLogDao.getCount())
        assertEquals(0, count)
    }

    @Test
    fun getRecent_returnsEntriesSortedByTimestampDesc() = runTest {
        // Arrange
        val timestamps = listOf(100L, 50L, 200L, 75L)
        timestamps.forEach { timestamp ->
            val entry = EventLog(timestamp = timestamp, message = "Message at $timestamp")
            eventLogDao.insert(entry)
        }

        // Act
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(4, result.size)
        assertEquals(200L, result[0].timestamp) // Most recent first
        assertEquals(100L, result[1].timestamp)
        assertEquals(75L, result[2].timestamp)
        assertEquals(50L, result[3].timestamp)  // Oldest last
    }

    @Test
    fun insert_generatesAutoIncrementId() = runTest {
        // Arrange
        val entry1 = EventLog(timestamp = 1000L, message = "First")
        val entry2 = EventLog(timestamp = 2000L, message = "Second")

        // Act
        eventLogDao.insert(entry1)
        eventLogDao.insert(entry2)
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(2, result.size)
        assertTrue(result[0].id > 0)
        assertTrue(result[1].id > 0)
        assertNotEquals(result[0].id, result[1].id)
    }

    @Test
    fun getRecent_withSameTimestamp_returnsAllEntries() = runTest {
        // Arrange
        val sameTimestamp = 1000L
        for (i in 1..3) {
            val entry = EventLog(timestamp = sameTimestamp, message = "Message $i")
            eventLogDao.insert(entry)
        }

        // Act
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(3, result.size)
        result.forEach { assertEquals(sameTimestamp, it.timestamp) }
    }

    @Test
    fun insert_withEmptyMessage_succeeds() = runTest {
        // Arrange
        val entry = EventLog(timestamp = 1000L, message = "")

        // Act
        eventLogDao.insert(entry)
        val result = getLiveDataValue(eventLogDao.getRecent())

        // Assert
        assertEquals(1, result.size)
        assertEquals("", result[0].message)
    }

    // Helper function to get LiveData value
    private fun <T> getLiveDataValue(liveData: androidx.lifecycle.LiveData<T>): T {
        var value: T? = null
        liveData.observeForever { value = it }
        return value!!
    }
}
