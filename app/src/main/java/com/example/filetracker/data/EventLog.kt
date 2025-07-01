package com.example.filetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_log")
data class EventLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val message: String
)