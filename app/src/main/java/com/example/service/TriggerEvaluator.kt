package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.DuaaApplication
import com.example.MainActivity
import com.example.data.database.DuaaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object TriggerEvaluator {

    private const val TAG = "TriggerEvaluator"
    private const val NOTIFICATION_CHANNEL_ID = "duaa_trigger_channel"
    private const val NOTIFICATION_ID = 9090

    fun evaluateTrigger(context: Context, triggerType: String, sourceName: String, extraText: String = "") {
        val application = context.applicationContext as? DuaaApplication ?: return
        val repository = application.repository

        // We launch a coroutine to check terms and play in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Read configurations
                val settings = repository.appSettings.first() ?: return@launch
                if (!settings.isGlobalAutomationEnabled) {
                    Log.d(TAG, "Global automation is disabled. Skipping trigger $triggerType")
                    return@launch
                }

                val allDuaas = repository.allDuaas.first()
                val targetDuaa = allDuaas.find { it.triggerType == triggerType && !it.isHidden }

                if (targetDuaa == null) {
                    Log.d(TAG, "No Duaa configured for trigger $triggerType")
                    return@launch
                }

                if (!targetDuaa.isEnabled) {
                    Log.d(TAG, "Duaa ${targetDuaa.name} is disabled. Skipping.")
                    return@launch
                }

                // Check Alarm Keyword Filter
                if (triggerType == "ALARM_DISMISSED" && settings.alarmKeywordFilter.isNotBlank()) {
                    val filters = settings.alarmKeywordFilter.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val matches = filters.any { filter ->
                        sourceName.contains(filter, ignoreCase = true) || extraText.contains(filter, ignoreCase = true)
                    }
                    if (!matches) {
                        Log.d(TAG, "Alarm did not match any of keyword filters in $filters. Skipping trigger.")
                        return@launch
                    }
                }

                if (triggerType == "CAR_CONNECT" || triggerType == "CAR_DISCONNECT") {
                    Log.d(TAG, "Delaying car trigger $triggerType for 5000ms to allow stable connection...")
                    kotlinx.coroutines.delay(5000)
                }

                // Both enabled! Play sound
                Log.d(TAG, "Triggering Duaa ${targetDuaa.name} (Source: $sourceName)")
                AudioService.startService(context, targetDuaa.soundResName, targetDuaa.name)

                // Add log entry
                repository.addTriggerLog(targetDuaa.id, targetDuaa.name, sourceName)

                // Show notification to user
                // Trigger notification removed as per user request
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating trigger $triggerType", e)
            }
        }
    }

    private fun showTriggerNotification(context: Context, duaa: DuaaEntity, source: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Уведомления о срабатывании Дуа",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Сообщает пользователю о воспроизведении дуа по событию"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Сработало дуа: ${duaa.name}")
            .setContentText("Событие: $source")
            .setSubText(duaa.description)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID + duaa.id, notification)
    }
}
