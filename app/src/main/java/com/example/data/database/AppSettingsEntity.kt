package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val isGlobalAutomationEnabled: Boolean = true,
    val homeSsid: String = "",
    val carBluetoothName: String = "",
    val homeLatitude: Double = 55.7558, // Default (can be updated by user)
    val homeLongitude: Double = 37.6173,
    val geofenceRadius: Float = 150.0f,
    val alarmKeywordFilter: String = "Работа",
    val detectedAlarms: String = "Любой будильник,Работа,Утро,Учеба,Тренировка",
    val isOnboardingCompleted: Boolean = false,
    val volume: Float = 0.5f
) {
    val alarmKeywordFilterList: List<String>
        get() = detectedAlarms.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
