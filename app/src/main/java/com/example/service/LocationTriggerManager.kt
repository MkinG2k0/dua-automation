package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.example.DuaaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object LocationTriggerManager {

    private const val TAG = "LocationTriggerManager"
    private var locationManager: LocationManager? = null
    private var isTracking = false
    private var wasInside = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            evaluateLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private var contextRef: Context? = null

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context) {
        if (isTracking) return
        
        contextRef = context.applicationContext
        
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

            // Prioritize NETWORK_PROVIDER for reliability and lower power, especially on virtual/CI devices
            if (isNetworkEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    30000L,
                    50f,
                    locationListener
                )
                isTracking = true
                Log.d(TAG, "Location tracking started with NETWORK_PROVIDER")
            } else if (isGpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    30000L, // 30 seconds interval
                    50f,    // 50 meters changes
                    locationListener
                )
                isTracking = true
                Log.d(TAG, "Location tracking started with GPS_PROVIDER")
            } else {
                Log.d(TAG, "No Location Providers enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking", e)
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        try {
            locationManager?.removeUpdates(locationListener)
            isTracking = false
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking", e)
        }
    }

    private fun evaluateLocation(location: Location) {
        val context = contextRef ?: return
        val application = context as? DuaaApplication ?: return
        val repository = application.repository

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = repository.appSettings.first() ?: return@launch
                val homeLat = settings.homeLatitude
                val homeLng = settings.homeLongitude
                val homeRadius = settings.geofenceRadius

                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    homeLat,
                    homeLng,
                    results
                )

                val currentDistance = results[0]
                val isInsideNow = currentDistance <= homeRadius

                Log.d(TAG, "Location check: Distance to home = $currentDistance meters, Is inside = $isInsideNow")

                if (isInsideNow && !wasInside) {
                    TriggerEvaluator.evaluateTrigger(context, "ENTER_HOME", "Geofence: Enter home zone")
                    wasInside = true
                } else if (!isInsideNow && wasInside) {
                    TriggerEvaluator.evaluateTrigger(context, "LEAVE_HOME", "Geofence: Exit home zone")
                    wasInside = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error evaluating location", e)
            }
        }
    }
}
