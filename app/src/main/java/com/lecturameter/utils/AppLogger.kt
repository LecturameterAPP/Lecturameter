package com.lecturameter.utils

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val LOG_FILE_NAME = "lecturameter_logs.txt"
    private const val MAX_LOG_SIZE_BYTES = 2 * 1024 * 1024 // 2 MB
    private lateinit var logFile: File
    private var isEnabled = true

    fun init(context: Context) {
        logFile = File(context.cacheDir, LOG_FILE_NAME)
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            logFile.delete()
        }
    }

    fun enableLogging(enabled: Boolean) {
        isEnabled = enabled
    }

    fun log(message: String, tag: String = "Lecturameter") {
        if (!isEnabled) return
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val entry = "[$timestamp] $tag: $message\n"
            if (::logFile.isInitialized) {
                FileWriter(logFile, true).use { it.append(entry) }
            }
            Log.d(tag, message)
        } catch (e: Exception) {
            Log.e("AppLogger", "Error writing log", e)
        }
    }

    fun logError(message: String, throwable: Throwable? = null, tag: String = "LecturameterError") {
        val errorMsg = if (throwable != null) "$message\n${sanitize(throwable.stackTraceToString())}" else message
        log(errorMsg, tag)
    }

    /**
     * Sanitiza un stack trace antes de escribirlo al log. Filtra:
     *  - Headers/parámetros con tokens (Authorization, Bearer, access_token, refresh_token, key=, api_key)
     *  - Rutas /data/data/<otros_paquetes>
     *  - Direcciones de email
     * Y trunca a las primeras 40 líneas (suficiente para diagnóstico).
     */
    private fun sanitize(stack: String): String {
        val patterns = listOf(
            Regex("(?i)(authorization\\s*:?\\s*bearer\\s+)\\S+") to "$1<REDACTED>",
            Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+") to "$1<REDACTED>",
            Regex("(?i)(access_token|refresh_token|id_token|api_key|key)=\\S+") to "$1=<REDACTED>",
            Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") to "<EMAIL_REDACTED>",
            Regex("ya29\\.[A-Za-z0-9._\\-]+") to "<OAUTH_TOKEN_REDACTED>"
        )
        var clean = stack
        for ((re, rep) in patterns) clean = clean.replace(re, rep)
        return clean.lineSequence().take(40).joinToString("\n")
    }

    fun getLogs(): String = try {
        if (::logFile.isInitialized && logFile.exists()) logFile.readText()
        else "No hay logs disponibles."
    } catch (e: Exception) { "Error al leer logs: ${e.message}" }

    fun clearLogs() {
        try {
            if (::logFile.isInitialized && logFile.exists()) logFile.delete()
            logFile.createNewFile()
        } catch (e: Exception) {
            Log.e("AppLogger", "Error clearing logs", e)
        }
    }

    fun getLogFileUri(context: Context): android.net.Uri? = try {
        if (::logFile.isInitialized && logFile.exists())
            FileProvider.getUriForFile(context, "${context.packageName}.provider", logFile)
        else null
    } catch (_: Exception) { null }
}
