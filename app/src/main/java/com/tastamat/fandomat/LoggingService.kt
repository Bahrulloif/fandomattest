package com.tastamat.fandomat

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LoggingService : Service() {

    companion object {
        const val CHANNEL_ID = "LoggingServiceChannel"
        const val NOTIFICATION_ID = 1
        const val PREFS_NAME = "logging_prefs"
        const val KEY_LOG_COUNTER = "log_counter"
        const val KEY_LOGGING_INTERVAL = "logging_interval"
        const val KEY_LOGGING_ACTIVE = "logging_active"
    }

    private var loggingJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val intervalSeconds = prefs.getLong(KEY_LOGGING_INTERVAL, 10)

        // Запускаем службу как foreground service
        val notification = createNotification("Логирование активно (интервал: ${intervalSeconds}с)")
        startForeground(NOTIFICATION_ID, notification)

        // Запускаем логирование
        startLoggingJob(intervalSeconds)

        return START_STICKY // Служба будет перезапущена после убийства
    }

    private fun startLoggingJob(intervalSeconds: Long) {
        loggingJob?.cancel()
        loggingJob = serviceScope.launch {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            while (isActive) {
                try {
                    val counter = prefs.getInt(KEY_LOG_COUNTER, 0) + 1
                    prefs.edit().putInt(KEY_LOG_COUNTER, counter).apply()

                    writeLog("Log entry #$counter: application running normally")

                    delay(intervalSeconds * 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun writeLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())
            val logEntry = "[$timestamp] $message\n"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeLogWithMediaStore(logEntry)
            } else {
                writeLogLegacy(logEntry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun writeLogWithMediaStore(logEntry: String) {
        val resolver = contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("logfile.txt")

        val cursor = resolver.query(downloadsUri, projection, selection, selectionArgs, null)
        val existingUri = if (cursor != null && cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val id = cursor.getLong(idColumn)
            cursor.close()
            android.net.Uri.withAppendedPath(downloadsUri, id.toString())
        } else {
            cursor?.close()
            null
        }

        if (existingUri != null) {
            resolver.openOutputStream(existingUri, "wa")?.use { output ->
                output.write(logEntry.toByteArray())
            }
        } else {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "logfile.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = resolver.insert(downloadsUri, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { output ->
                    output.write(logEntry.toByteArray())
                }
            }
        }
    }

    private fun writeLogLegacy(logEntry: String) {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logFile = File(downloadsDir, "logfile.txt")

        FileOutputStream(logFile, true).use { output ->
            output.write(logEntry.toByteArray())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Служба логирования",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображает статус фоновой записи логов"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FandomatTest")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        loggingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
