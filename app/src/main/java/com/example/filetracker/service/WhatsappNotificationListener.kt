package com.example.filetracker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.filetracker.util.EventLogger

class WhatsappNotificationListener : NotificationListenerService() {
    private var lastMessage: String? = null

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.whatsapp") {
            if (sbn.key == lastMessage) return // Уже обработали

            val extras = sbn.notification.extras
            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")?.toString()
            val bigText = extras.getCharSequence("android.bigText")?.toString()
            val message = when {
                !bigText.isNullOrEmpty() -> bigText
                !text.isNullOrEmpty() -> text
                else -> null
            }
            if (!message.isNullOrEmpty() && message != lastMessage) {
                val logMsg = "WhatsApp: $title: $message"
                Log.d("WA_NOTIFY", logMsg)
                EventLogger.log(this, logMsg)
                lastMessage = message
            }
        }
    }
} 