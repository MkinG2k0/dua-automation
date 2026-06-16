package com.example.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.example.DuaaApplication
import com.example.service.TriggerEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SystemEventsReceiver"
        private var isFirstProcessRun = true
    }

    private fun getLastSavedSsid(context: Context): String {
        val prefs = context.getSharedPreferences("wifi_state_prefs", Context.MODE_PRIVATE)
        return prefs.getString("last_connected_ssid", "") ?: ""
    }

    private fun saveLastSsid(context: Context, ssid: String) {
        val prefs = context.getSharedPreferences("wifi_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_connected_ssid", ssid).apply()
    }

    private fun getSsidSafely(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager?.connectionInfo
            var ssid = wifiInfo?.ssid ?: ""
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                return ssid
            }

            // Fallback: Try ConnectivityManager WifiInfo mapping if available
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork
                    val capabilities = cm.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                        val transportInfo = capabilities.transportInfo as? android.net.wifi.WifiInfo
                        var tSsid = transportInfo?.ssid ?: ""
                        if (tSsid.startsWith("\"") && tSsid.endsWith("\"")) {
                            tSsid = tSsid.substring(1, tSsid.length - 1)
                        }
                        if (tSsid.isNotEmpty() && tSsid != "<unknown ssid>") {
                            return tSsid
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        Log.d(TAG, "SystemEventsReceiver received: $action")

        val application = context.applicationContext as? DuaaApplication ?: return
        val repository = application.repository

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = device?.name ?: "Неизвестное Bluetooth устройство"
                Log.d(TAG, "Bluetooth connected: $deviceName")

                CoroutineScope(Dispatchers.IO).launch {
                    val settings = repository.getAppSettings() ?: return@launch
                    val filterNames = settings.carBluetoothName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val matches = filterNames.any { filter ->
                        deviceName.contains(filter, ignoreCase = true) || filter.contains(deviceName, ignoreCase = true)
                    }
                    if (matches) {
                        TriggerEvaluator.evaluateTrigger(context, "CAR_CONNECT", "Bluetooth: $deviceName")
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = device?.name ?: "Неизвестное Bluetooth устройство"
                Log.d(TAG, "Bluetooth disconnected: $deviceName")
 
                CoroutineScope(Dispatchers.IO).launch {
                    val settings = repository.getAppSettings() ?: return@launch
                    val filterNames = settings.carBluetoothName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val matches = filterNames.any { filter ->
                        deviceName.contains(filter, ignoreCase = true) || filter.contains(deviceName, ignoreCase = true)
                    }
                    if (matches) {
                        TriggerEvaluator.evaluateTrigger(context, "CAR_DISCONNECT", "Bluetooth: $deviceName отключен")
                    }
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION, "android.net.conn.CONNECTIVITY_CHANGE" -> {
                var isWifiConnected = false
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    isWifiConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val activeNetwork = cm?.activeNetwork
                        val capabilities = cm?.getNetworkCapabilities(activeNetwork)
                        capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    } else {
                        @Suppress("DEPRECATION")
                        cm?.activeNetworkInfo?.type == android.net.ConnectivityManager.TYPE_WIFI && cm.activeNetworkInfo?.isConnected == true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check wifi connection status", e)
                }

                if (isWifiConnected) {
                    val ssid = getSsidSafely(context)
                    Log.d(TAG, "Wi-Fi is connected. Found SSID: $ssid")
                    if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                        if (isFirstProcessRun) {
                            Log.d(TAG, "First wifi broadcast on app launch. Saving SSID '$ssid' without triggering ENTER_HOME")
                            saveLastSsid(context, ssid)
                            isFirstProcessRun = false
                            return
                        }

                        val lastSsid = getLastSavedSsid(context)
                        if (ssid != lastSsid) {
                            saveLastSsid(context, ssid)
                            Log.d(TAG, "Wi-Fi SSID transitioned from '$lastSsid' to '$ssid'. Checking for home network...")
                            CoroutineScope(Dispatchers.IO).launch {
                                val settings = repository.getAppSettings() ?: return@launch
                                val ssidFilters = settings.homeSsid.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val matches = ssidFilters.any { filter -> ssid.trim().equals(filter, ignoreCase = true) }
                                if (matches) {
                                    TriggerEvaluator.evaluateTrigger(context, "ENTER_HOME", "WI-FI SSID: $ssid")
                                }
                            }
                        } else {
                            Log.d(TAG, "Wi-Fi SSID '$ssid' matches last saved SSID. Under same connection state, skip trigger.")
                        }
                    }
                } else {
                    if (isFirstProcessRun) {
                        Log.d(TAG, "First wifi broadcast on app launch (disconnected). Initializing last SSID to empty.")
                        saveLastSsid(context, "")
                        isFirstProcessRun = false
                        return
                    }

                    val lastSsid = getLastSavedSsid(context)
                    if (lastSsid.isNotEmpty()) {
                        saveLastSsid(context, "")
                        Log.d(TAG, "Wi-Fi disconnected. Previous active SSID was '$lastSsid'. Checking if previous was home Wi-Fi...")
                        CoroutineScope(Dispatchers.IO).launch {
                            val settings = repository.getAppSettings() ?: return@launch
                            val ssidFilters = settings.homeSsid.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            val wasHomeSsid = ssidFilters.any { filter -> lastSsid.trim().equals(filter, ignoreCase = true) }
                            if (wasHomeSsid) {
                                TriggerEvaluator.evaluateTrigger(context, "LEAVE_HOME", "Сеть WI-FI отключена от дома")
                            } else {
                                Log.d(TAG, "Disconnected from non-home Wi-Fi '$lastSsid'. Skipping LEAVE_HOME trigger.")
                            }
                        }
                    }
                }
            }
        }
    }
}
