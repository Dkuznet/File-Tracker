package com.example.filetracker.util

import android.content.Context
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object EventLogger {
    // Кэш: сообщение -> время последнего логирования (мс)
    private val messageCache = ConcurrentHashMap<String, Long>()
    private const val CACHE_DURATION_MS = 60_000L // 1 минута

    fun log(context: Context, message: String) {
        val now = System.currentTimeMillis()
        // Очистка устаревших записей
        messageCache.entries.removeIf { now - it.value > CACHE_DURATION_MS }
        val lastLogged = messageCache[message]
        if (lastLogged != null && now - lastLogged < CACHE_DURATION_MS) {
            // Сообщение уже было залогировано недавно, пропускаем
            return
        }
        messageCache[message] = now
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).eventLogDao().insert(
                EventLog(timestamp = now, message = message)
            )
        }
    }
}