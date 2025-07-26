package com.example.filetracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers")
    fun getAll(): LiveData<List<Tracker>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tracker: Tracker): Long

    @Delete
    suspend fun delete(tracker: Tracker)

    @Query("UPDATE trackers SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("UPDATE trackers SET watchSubfolders = :watchSubfolders WHERE id = :id")
    suspend fun setWatchSubfolders(id: Long, watchSubfolders: Boolean)
}