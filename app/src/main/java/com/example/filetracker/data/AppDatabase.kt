package com.example.filetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.filetracker.FitTracker

@Database(entities = [Tracker::class, EventLog::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerDao(): TrackerDao
    abstract fun eventLogDao(): EventLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase().also { INSTANCE = it }
            }

        private fun buildDatabase(): AppDatabase {
            val context: Context = FitTracker.instance.applicationContext
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "file_tracker_db"
            )
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}