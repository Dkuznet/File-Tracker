package com.example.filetracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Tracker::class, EventLog::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun eventLogDao(): EventLogDao

}
