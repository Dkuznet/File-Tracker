package com.example.filetracker

import android.content.Context
import android.os.Handler
import com.example.filetracker.service.MediaContentObserver
import com.example.filetracker.service.MediaType
import com.example.filetracker.util.FileUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

class MediaContentObserverTest {
    @Test
    fun isFileAlreadyProcessed_returnsTrue_whenFileInCacheAndNotExpired() {
        val context = mock<Context>()
        val handler = mock<Handler>()
        val observer = MediaContentObserver(context, handler, MediaType.IMAGE, "/output")
        val path = "/storage/emulated/0/com.whatsapp/file.jpg"
        val dateAdded = System.currentTimeMillis() / 1000

        // Добавляем файл в кэш
        val addFileToCache = observer.javaClass.getDeclaredMethod(
            "addFileToCache",
            String::class.java,
            Long::class.java
        )
        addFileToCache.isAccessible = true
        addFileToCache.invoke(observer, path, dateAdded)

        // Проверяем, что файл считается обработанным
        val isFileAlreadyProcessed = observer.javaClass.getDeclaredMethod(
            "isFileAlreadyProcessed",
            String::class.java,
            Long::class.java
        )
        isFileAlreadyProcessed.isAccessible = true
        val result = isFileAlreadyProcessed.invoke(observer, path, dateAdded) as Boolean

        assertTrue(result)
    }

    @Test
    fun handleNewFile_doesNotCopyFile_whenFileDoesNotMeetConditions() {
        val context = mock<Context>()
        val handler = mock<Handler>()
        val observer = MediaContentObserver(context, handler, MediaType.IMAGE, "/output")
        val sourcePath = "/storage/emulated/0/com.whatsapp/file.jpg"

        val fileUtilsMock: MockedStatic<FileUtils> = Mockito.mockStatic(FileUtils::class.java)
        try {
            whenever(FileUtils.checkFileConditions(context, sourcePath)).thenReturn(false)

            val handleNewFile = observer.javaClass.getDeclaredMethod(
                "handleNewFile",
                Context::class.java,
                String::class.java
            )
            handleNewFile.isAccessible = true
            handleNewFile.invoke(observer, context, sourcePath)

            fileUtilsMock.verify({ FileUtils.fileCopy(any(), anyOrNull(), anyOrNull()) }, never())
        } finally {
            fileUtilsMock.close()
        }
    }
} 