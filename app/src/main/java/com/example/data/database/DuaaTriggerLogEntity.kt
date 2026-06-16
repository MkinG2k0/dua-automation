package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trigger_logs")
data class DuaaTriggerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val duaaId: Int,
    val duaaName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerSource: String // "ALARM", "WI-FI", "BLUETOOTH", "GEOFENCE", "TEST"
)
