package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.DuaaApplication
import com.example.data.database.AppSettingsEntity
import com.example.data.database.DuaaEntity
import com.example.data.database.DuaaTriggerLogEntity
import com.example.data.repository.DuaaRepository
import com.example.service.AudioService
import com.example.service.TriggerEvaluator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DuaaViewModel(private val repository: DuaaRepository) : ViewModel() {

    val duaaList: StateFlow<List<DuaaEntity>> = repository.allDuaas
        .map { list -> list.filter { !it.isHidden } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val appSettings: StateFlow<AppSettingsEntity?> = repository.appSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettingsEntity()
        )

    val triggerLogs: StateFlow<List<DuaaTriggerLogEntity>> = repository.triggerLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleGlobalAutomation(newValue: Boolean) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(isGlobalAutomationEnabled = newValue)
            repository.updateAppSettings(updated)
        }
    }

    fun toggleDuaa(duaa: DuaaEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.updateDuaa(duaa.copy(isEnabled = isEnabled))
        }
    }

    fun saveWifiSsid(ssid: String) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(homeSsid = ssid)
            repository.updateAppSettings(updated)
        }
    }

    fun saveBluetoothFilter(name: String) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(carBluetoothName = name)
            repository.updateAppSettings(updated)
        }
    }

    fun saveAlarmKeywordFilter(keyword: String) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(alarmKeywordFilter = keyword)
            repository.updateAppSettings(updated)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(isOnboardingCompleted = completed)
            repository.updateAppSettings(updated)
        }
    }

    fun saveGeofenceSettings(lat: Double, lng: Double, radius: Float) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(
                homeLatitude = lat,
                homeLongitude = lng,
                geofenceRadius = radius
            )
            repository.updateAppSettings(updated)
        }
    }

    fun saveVolume(volume: Float) {
        viewModelScope.launch {
            val currentSettings = appSettings.value ?: AppSettingsEntity()
            val updated = currentSettings.copy(volume = volume)
            repository.updateAppSettings(updated)
        }
    }

    fun simulateTrigger(context: Context, triggerType: String, sourceName: String, extraText: String = "") {
        TriggerEvaluator.evaluateTrigger(context, triggerType, sourceName, extraText)
    }

    fun testPlayDuaa(context: Context, duaa: DuaaEntity) {
        // Immediate playback bypasses global checks for test buttons
        AudioService.startService(context, duaa.soundResName, "${duaa.name} (Test)")
        viewModelScope.launch {
            repository.addTriggerLog(duaa.id, duaa.name, "Manual test play")
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DuaaViewModel::class.java)) {
                val repository = (context.applicationContext as DuaaApplication).repository
                @Suppress("UNCHECKED_CAST")
                return DuaaViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
