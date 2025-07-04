package com.example.filetracker.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.filetracker.util.EventLogger

class MediaContentObserver(
    private val context: Context,
    handler: Handler
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d("MediaContentObserver", "Изменение в MediaStore: $uri")
        EventLogger.log(context, "MediaContentObserver Изменение в MediaStore: $uri")
        checkNewFiles()
    }

    private fun checkNewFiles() {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA, // Путь к файлу
            MediaStore.Images.Media.DATE_ADDED // Время добавления
        )

        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%WhatsApp%")

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                )
                val dateAdded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                )
                Log.d("MediaContentObserver", "Новый файл: $path, добавлен: $dateAdded")
                EventLogger.log(
                    context,
                    "MediaContentObserver Новый файл: $path, добавлен: $dateAdded"
                )
                // Здесь можно обработать новый файл
            }
        }
    }
}

// Регистрация ContentObserver
fun registerMediaObserver(context: Context) {
    val handler = Handler(Looper.getMainLooper())
    val observer = MediaContentObserver(context, handler)
    context.contentResolver.registerContentObserver(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        true, // Уведомлять о любых изменениях в поддереве
        observer
    )
}