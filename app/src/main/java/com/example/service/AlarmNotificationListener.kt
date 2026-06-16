package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.DuaaApplication
import com.example.data.database.AppSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "AlarmNotificationListener"
    }

    // Hashset to keep track of active ringing alarm notification keys to trigger only on dismiss
    private val activeAlarms = HashSet<String>()
    
    private fun saveDetectedAlarm(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        
        // Skip common purely temporal system notifications or "upcoming" / "предстоящ" indicators
        val isUpcomingIndicator = cleanTitle.contains("upcoming", ignoreCase = true) || 
                                  cleanTitle.contains("предстоящ", ignoreCase = true) ||
                                  cleanTitle.all { it.isDigit() || it == ':' || it == ' ' || it.uppercaseChar() == 'A' || it.uppercaseChar() == 'P' || it.uppercaseChar() == 'M' }
        if (isUpcomingIndicator) return

        val app = applicationContext as? DuaaApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = app.repository
                val settings = repository.getAppSettings() ?: AppSettingsEntity()
                val existing = settings.alarmKeywordFilterList
                if (!existing.contains(cleanTitle)) {
                    val newList = (existing + cleanTitle).distinct()
                    val updated = settings.copy(detectedAlarms = newList.joinToString(","))
                    repository.updateAppSettings(updated)
                    Log.d(TAG, "Successfully added detected clock alarm to settings: $cleanTitle")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update detected alarms list in database", e)
            }
        }
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val sbnNotNull = sbn ?: return
        val packageName = sbnNotNull.packageName ?: return
        val notification = sbnNotNull.notification ?: return

        // Check if package indicates Google Clock or a standard alarm app
        val isClockAlarm = packageName == "com.google.android.deskclock" || 
                           packageName.contains("clock", ignoreCase = true) || 
                           packageName.contains("alarm", ignoreCase = true)
                           
        val isAlarmCategory = notification.category == Notification.CATEGORY_ALARM || notification.category == "alarm"

        if (isClockAlarm || isAlarmCategory) {
            val title = notification.extras?.getString("android.title") ?: ""
            val text = notification.extras?.getCharSequence("android.text")?.toString() ?: ""
            
            // Exclude "upcoming" notifications
            val isUpcoming = title.contains("upcoming", ignoreCase = true) || 
                             title.contains("предстоящ", ignoreCase = true) ||
                             text.contains("upcoming", ignoreCase = true) ||
                             text.contains("предстоящ", ignoreCase = true)

            Log.d(TAG, "Notification posted: pkg=$packageName, title=$title, ongoing=${sbnNotNull.isOngoing}, isUpcoming=$isUpcoming")

            // Register valid non-numerical/non-upcoming alarm labels to selection list
            saveDetectedAlarm(title)

            if (sbnNotNull.isOngoing && !isUpcoming) {
                val key = sbnNotNull.key
                if (key != null) {
                    synchronized(activeAlarms) {
                        activeAlarms.add(key)
                    }
                    Log.d(TAG, "Tracking active ringing alarm. Key: $key")
                }
            }
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val sbnNotNull = sbn ?: return
        val packageName = sbnNotNull.packageName ?: return
        val notification = sbnNotNull.notification ?: return
        val key = sbnNotNull.key ?: return
        
        val wasActive = synchronized(activeAlarms) {
            activeAlarms.remove(key)
        }

        if (wasActive) {
            val title = notification.extras?.getString("android.title") ?: ""
            val text = notification.extras?.getCharSequence("android.text")?.toString() ?: ""
            val bigText = notification.extras?.getCharSequence("android.bigText")?.toString() ?: ""
            val fullContentText = "$title $text $bigText".trim()
            
            Log.d(TAG, "Detected alarm notification dismissed. Key: $key, Package: $packageName, title: $title, text: $text")
            
            TriggerEvaluator.evaluateTrigger(
                context = this,
                triggerType = "ALARM_DISMISSED",
                sourceName = if (title.isNotBlank()) "Будильник ($title) отключен" else "Будильник отключен",
                extraText = fullContentText
            )
        } else {
            Log.d(TAG, "Alarm notification removed, but was not tracked as active ringing (likely upcoming or non-ringing). Key: $key")
        }
    }
}
