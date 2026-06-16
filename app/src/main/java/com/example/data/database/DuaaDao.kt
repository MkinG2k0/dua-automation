package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DuaaDao {
    @Query("SELECT * FROM duaas ORDER BY id ASC")
    fun getAllDuaas(): Flow<List<DuaaEntity>>

    @Query("SELECT * FROM duaas ORDER BY id ASC")
    suspend fun getAllDuaasOnce(): List<DuaaEntity>

    @Update
    suspend fun updateDuaa(duaa: DuaaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDuaas(duaas: List<DuaaEntity>)

    @Query("SELECT * FROM duaas WHERE id = :id LIMIT 1")
    suspend fun getDuaaById(id: Int): DuaaEntity?

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getAppSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getAppSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAppSettings(settings: AppSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTriggerLog(log: DuaaTriggerLogEntity)

    @Query("SELECT * FROM trigger_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllTriggerLogs(): Flow<List<DuaaTriggerLogEntity>>

    @Query("DELETE FROM trigger_logs")
    suspend fun clearLogs()
}
