package com.tastamat.fandomat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Проверяем, было ли логирование активно до перезагрузки
            val prefs = context.getSharedPreferences("logging_prefs", Context.MODE_PRIVATE)
            val isLoggingActive = prefs.getBoolean("logging_active", false)

            if (isLoggingActive) {
                // Перезапускаем службу логирования
                val serviceIntent = Intent(context, LoggingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
