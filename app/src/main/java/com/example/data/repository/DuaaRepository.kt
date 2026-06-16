package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppSettingsEntity
import com.example.data.database.DuaaDao
import com.example.data.database.DuaaEntity
import com.example.data.database.DuaaTriggerLogEntity
import kotlinx.coroutines.flow.Flow

class DuaaRepository(private val duaaDao: DuaaDao) {

    val allDuaas: Flow<List<DuaaEntity>> = duaaDao.getAllDuaas()
    val appSettings: Flow<AppSettingsEntity?> = duaaDao.getAppSettingsFlow()
    val triggerLogs: Flow<List<DuaaTriggerLogEntity>> = duaaDao.getAllTriggerLogs()

    suspend fun updateDuaa(duaa: DuaaEntity) {
        duaaDao.updateDuaa(duaa)
    }

    suspend fun getAppSettings(): AppSettingsEntity? {
        return duaaDao.getAppSettings()
    }

    suspend fun getDuaaById(id: Int): DuaaEntity? {
        return duaaDao.getDuaaById(id)
    }

    suspend fun updateAppSettings(settings: AppSettingsEntity) {
        duaaDao.saveAppSettings(settings)
    }

    suspend fun addTriggerLog(duaaId: Int, duaaName: String, triggerSource: String) {
        val log = DuaaTriggerLogEntity(
            duaaId = duaaId,
            duaaName = duaaName,
            triggerSource = triggerSource
        )
        duaaDao.insertTriggerLog(log)
    }

    suspend fun clearLogs() {
        duaaDao.clearLogs()
    }
}
