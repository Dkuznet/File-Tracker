package com.example.filetracker.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(entry: EventLog)

    @Query("SELECT * FROM (SELECT * FROM event_log ORDER BY timestamp DESC LIMIT 100) ORDER BY timestamp ASC ")
    fun getRecent(): LiveData<List<EventLog>>

    @Query("DELETE FROM event_log")
    suspend fun clear()
}