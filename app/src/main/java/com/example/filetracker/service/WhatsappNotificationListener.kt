package com.example.filetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.filetracker.util.EventLogger

class WhatsappNotificationListener : NotificationListenerService() {

    // Кэш сообщений: сообщение -> время получения (мс)
    private val recentMessages = HashMap<String, Long>()
    private val CACHE_DURATION_MS = 60_000L // 1 минута

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.whatsapp") {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()
            val message = when {
                !bigText.isNullOrEmpty() -> bigText
                !text.isNullOrEmpty() -> text
                else -> null
            }
            val logMsg = "WhatsApp: $title: $message"
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
                Log.d("WA_NOTIFY", logMsg)
                EventLogger.log(this, logMsg)

                recentMessages[logMsg] = now
            }
        }
    }
} 