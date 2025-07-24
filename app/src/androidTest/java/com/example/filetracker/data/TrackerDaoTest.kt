package com.example.filetracker.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class TrackerDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var trackerDao: TrackerDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        trackerDao = database.trackerDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_returnsGeneratedId() = runTest {
        val tracker = Tracker(sourceDir = "/source", destDir = "/dest")

        val id = trackerDao.insert(tracker)

        Assert.assertTrue(id > 0)
    }

    @Test
    fun getAll_returnsAllTrackers() = runTest {
        val tracker1 = Tracker(sourceDir = "/source1", destDir = "/dest1")
        val tracker2 = Tracker(sourceDir = "/source2", destDir = "/dest2", isActive = false)

        trackerDao.insert(tracker1)
        trackerDao.insert(tracker2)
        val result = getLiveDataValue(trackerDao.getAll())

        Assert.assertEquals(2, result.size)
    }

    @Test
    fun delete_removesTracker() = runTest {
        val tracker = Tracker(sourceDir = "/source", destDir = "/dest")
        val id = trackerDao.insert(tracker)
        val insertedTracker = tracker.copy(id = id)

        trackerDao.delete(insertedTracker)
        val result = getLiveDataValue(trackerDao.getAll())

        Assert.assertEquals(0, result.size)
    }

    @Test
    fun setActive_updatesIsActiveField() = runTest {
        val tracker = Tracker(sourceDir = "/source", destDir = "/dest", isActive = true)
        val id = trackerDao.insert(tracker)

        trackerDao.setActive(id, false)
        val result = getLiveDataValue(trackerDao.getAll())

        Assert.assertEquals(1, result.size)
        Assert.assertFalse(result[0].isActive)
    }

    private fun <T> getLiveDataValue(liveData: LiveData<T>): T {
        var value: T? = null
        liveData.observeForever { value = it }
        return value!!
    }
}