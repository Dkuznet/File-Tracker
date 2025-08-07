package com.example.filetracker.util

import android.content.Context
import android.util.Log
import com.example.filetracker.FitTracker
import com.example.filetracker.data.AppDatabase
import com.example.filetracker.data.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT
}

object EventLogger {
    // Кэш: сообщение -> время последнего логирования (мс)
    private val messageCache = ConcurrentHashMap<String, Long>()
    private const val CACHE_DURATION_MS = 60_000L // 1 минута

    fun log(
        message: String,
        logTag: String? = null,
        log: LogLevel = LogLevel.DEBUG,
        extra: Boolean = false,
        type: String = "system"
    ) {
        val effectiveContext = FitTracker.instance.applicationContext  // Глобальный fallback

        val prefs = effectiveContext.getSharedPreferences("eventlog_prefs", Context.MODE_PRIVATE)
        val extendedLogging = prefs.getBoolean("extended_logging", false)
        // Если это расширенный лог, а режим не включён — не пишем
        if (extra && !extendedLogging) return

        logTag?.let { // Использование let для обработки nullable logTag
            when (log) {
                LogLevel.DEBUG -> Log.d(it, message)
                LogLevel.WARN -> Log.w(it, message)
                LogLevel.ERROR -> Log.e(it, message)
                LogLevel.INFO -> Log.i(it, message)
                LogLevel.VERBOSE -> Log.v(it, message)
                LogLevel.ASSERT -> Log.wtf(it, message)
            }
        }

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
            AppDatabase.getDatabase().eventLogDao().insert(
                EventLog(timestamp = now, message = message, type = if (extra) "extended" else type)
            )
        }
    }
}