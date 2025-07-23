package com.example.filetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.filetracker.data.AppNameRepository
import com.example.filetracker.util.EventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AppNotificationListener : NotificationListenerService() {

    // Кэш сообщений: сообщение -> время получения (мс)
    private val recentMessages = HashMap<String, Long>()
    private val CACHE_DURATION_MS = 60_000L // 1 минута
    private var appName: String? = null

    override fun onCreate() {
        super.onCreate()
        // Загружаем app_name из DataStore при старте сервиса
        CoroutineScope(Dispatchers.IO).launch {
            appName = AppNameRepository.getAppName(this@AppNotificationListener)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Получаем app_name из памяти, если нет — пробуем синхронно загрузить
        val currentAppName =
            appName ?: runBlocking { AppNameRepository.getAppName(this@AppNotificationListener) }
        if (currentAppName != null) {
            if (sbn.packageName != currentAppName) return
        }
        val extras = sbn.notification.extras
        val title = extras.getString("android.title")
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val message = when {
            !bigText.isNullOrEmpty() -> bigText
            !text.isNullOrEmpty() -> text
            else -> null
        }
        val logMsg = "$title: $message"
        val now = System.currentTimeMillis()

        // Очистка устаревших сообщений из кэша
        val iterator = recentMessages.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > CACHE_DURATION_MS) {
                iterator.remove()
            }
        }

        // Если сообщение уже было за последнюю минуту — игнорируем
        if (recentMessages.containsKey(logMsg)) return

        if (!message.isNullOrEmpty()) {
            EventLogger.log(message = logMsg)
            recentMessages[logMsg] = now
        }
    }
} 