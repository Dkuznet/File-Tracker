package com.example.filetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trackers")
data class Tracker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceDir: String,
    val destDir: String,
    val isActive: Boolean = true // новое поле
)