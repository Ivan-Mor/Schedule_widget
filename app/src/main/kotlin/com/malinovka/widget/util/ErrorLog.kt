package com.malinovka.widget.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Простейший журнал ошибок парсеров/источников в файл (filesDir/logs/errors.log).
 * Ротация: при превышении 256 КБ текущий файл становится errors.1.log.
 * Сбой логгера никогда не должен ронять вызывающего.
 */
object ErrorLog {
    private const val MAX_BYTES = 256 * 1024
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "logs").apply { mkdirs() }
    }

    fun log(tag: String, error: Throwable? = null, message: String = "") {
        try {
            val directory = dir ?: return
            val file = File(directory, "errors.log")
            if (file.exists() && file.length() > MAX_BYTES) {
                val prev = File(directory, "errors.1.log")
                prev.delete()
                file.renameTo(prev)
            }
            val line = buildString {
                append(fmt.format(Date())).append(" [").append(tag).append("] ")
                if (message.isNotEmpty()) append(message)
                if (error != null) {
                    if (isNotEmpty()) append(" · ")
                    append(error.javaClass.simpleName)
                    append(": ")
                    append(error.message)
                }
                append('\n')
            }
            file.appendText(line)
        } catch (_: Exception) {
            // Логирование не должно ломать работу виджета.
        }
    }
}
