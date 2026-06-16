package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "duaas")
data class DuaaEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val description: String,
    val triggerType: String, // "ALARM_DISMISSED", "LEAVE_HOME", "ENTER_HOME", "CAR_CONNECT", "CAR_DISCONNECT"
    val soundResName: String,
    val isEnabled: Boolean = true,
    val isHidden: Boolean = false
)
