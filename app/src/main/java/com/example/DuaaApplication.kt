package com.example

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.example.data.database.DuaaDatabase
import com.example.data.repository.DuaaRepository
import com.example.receiver.SystemEventsReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class DuaaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    private val systemEventsReceiver = SystemEventsReceiver()

    val database by lazy { DuaaDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { DuaaRepository(database.duaaDao()) }

    override fun onCreate() {
        super.onCreate()

        // Register system events receiver globally for background state updates
        val intentFilter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction("android.net.conn.CONNECTIVITY_CHANGE")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            systemEventsReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }
}
