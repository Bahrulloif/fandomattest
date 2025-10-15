package com.tastamat.fandomat

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tastamat.fandomat.ui.theme.FandomattestTheme
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private companion object {
        const val PREFS_NAME = "logging_prefs"
        const val KEY_LOGGING_ACTIVE = "logging_active"
        const val KEY_LOGGING_INTERVAL = "logging_interval"
        const val KEY_LOG_COUNTER = "log_counter"
    }

    private lateinit var prefs: android.content.SharedPreferences

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Разрешения предоставлены", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Необходимы разрешения для работы", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        checkPermissions()

        // Логируем запуск приложения
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("App started")
        }

        // Восстанавливаем состояние логирования при запуске
        val isLoggingActive = prefs.getBoolean(KEY_LOGGING_ACTIVE, false)
        if (isLoggingActive) {
            val interval = prefs.getLong(KEY_LOGGING_INTERVAL, 10)
            startLogging(interval)
        }

        setContent {
            FandomattestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoggingScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Логируем открытие главного экрана
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("MainActivity displayed on screen")
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ использует Scoped Storage, разрешения не нужны для Downloads
            return
        }

        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @Composable
    fun LoggingScreen() {
        val isLoggingActive = prefs.getBoolean(KEY_LOGGING_ACTIVE, false)
        val savedInterval = prefs.getLong(KEY_LOGGING_INTERVAL, 10)

        var intervalText by remember { mutableStateOf(TextFieldValue(savedInterval.toString())) }
        var isLogging by remember { mutableStateOf(isLoggingActive) }
        var statusText by remember {
            mutableStateOf(
                if (isLoggingActive) "Логирование активно (интервал: ${savedInterval}с)"
                else "Логирование остановлено"
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "FandomatTest",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Поле ввода интервала
            OutlinedTextField(
                value = intervalText,
                onValueChange = { intervalText = it },
                label = { Text("Интервал (секунды)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLogging
            )

            // Кнопки старт/стоп
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val interval = intervalText.text.toLongOrNull()
                        if (interval != null && interval > 0) {
                            startLogging(interval)
                            isLogging = true
                            statusText = "Логирование активно (интервал: ${interval}с)"
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Введите корректный интервал",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLogging
                ) {
                    Text("Старт логирования")
                }

                Button(
                    onClick = {
                        stopLogging()
                        isLogging = false
                        statusText = "Логирование остановлено"
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isLogging
                ) {
                    Text("Стоп логирования")
                }
            }

            // Статус
            Text(
                text = "Статус: $statusText",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Кнопки управления
            Text(
                text = "Управление приложением:",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { crashApp() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Сломать приложение")
            }

            Button(
                onClick = { freezeApp() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Зависнуть")
            }

            Button(
                onClick = { resetApp() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сбросить приложение")
            }

            Button(
                onClick = {
                    // Логируем перед очисткой
                    CoroutineScope(Dispatchers.IO).launch {
                        writeLog("User triggered: Clear logs")
                        delay(100) // Даем время записать лог
                        clearLogs()
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Логи очищены",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Очистить логи")
            }

            Button(
                onClick = { exitApp() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Выход")
            }
        }
    }

    private fun startLogging(intervalSeconds: Long) {
        // Сохраняем состояние в SharedPreferences
        prefs.edit().apply {
            putBoolean(KEY_LOGGING_ACTIVE, true)
            putLong(KEY_LOGGING_INTERVAL, intervalSeconds)
            putInt(KEY_LOG_COUNTER, 0)
            apply()
        }

        // Запускаем Foreground Service
        val serviceIntent = Intent(this, LoggingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopLogging() {
        // Сохраняем состояние остановки
        prefs.edit().apply {
            putBoolean(KEY_LOGGING_ACTIVE, false)
            apply()
        }

        // Останавливаем службу
        val serviceIntent = Intent(this, LoggingService::class.java)
        stopService(serviceIntent)
    }

    private fun writeLog(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())
            val logEntry = "[$timestamp] $message\n"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - используем MediaStore API
                writeLogWithMediaStore(logEntry)
            } else {
                // Android 9 и ниже - прямая запись в файл
                writeLogLegacy(logEntry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Ошибка записи лога: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun writeLogWithMediaStore(logEntry: String) {
        val resolver = contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // Проверяем, существует ли файл
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
            // Дописываем в существующий файл
            resolver.openOutputStream(existingUri, "wa")?.use { output ->
                output.write(logEntry.toByteArray())
            }
        } else {
            // Создаем новый файл
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

    private fun clearLogs() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - удаляем через MediaStore
                val resolver = contentResolver
                val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf("logfile.txt")
                resolver.delete(downloadsUri, selection, selectionArgs)
            } else {
                // Android 9 и ниже
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val logFile = File(downloadsDir, "logfile.txt")
                if (logFile.exists()) {
                    logFile.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка очистки логов: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crashApp() {
        // Логируем перед крашем
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("User triggered: App crash")
            delay(100) // Даем время записать лог
            throw RuntimeException("App crashed!")
        }
    }

    @Suppress("ControlFlowWithEmptyBody")
    private fun freezeApp() {
        // Логируем перед зависанием
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("User triggered: App freeze")
            delay(100) // Даем время записать лог
            // Блокируем главный поток на main thread
            runOnUiThread {
                while (true) {
                    // Бесконечный цикл
                }
            }
        }
    }

    private fun resetApp() {
        // Логируем перед сбросом
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("User triggered: App reset")
            delay(100) // Даем время записать лог
            val intent = Intent(this@MainActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun exitApp() {
        // Логируем перед выходом
        CoroutineScope(Dispatchers.IO).launch {
            writeLog("User triggered: App exit")
            delay(100) // Даем время записать лог
            stopLogging()
            finishAffinity()
            exitProcess(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogging()
    }
}