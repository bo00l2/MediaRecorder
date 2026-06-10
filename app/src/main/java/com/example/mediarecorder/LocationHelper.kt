package com.example.mediarecorder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationHelper(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        try {
            val task = fusedLocationClient.lastLocation
            Tasks.await(task)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLocalName(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val adminArea = address.adminArea ?: ""
                val locality = address.locality ?: address.subAdminArea ?: ""
                val subLocality = address.subLocality ?: address.thoroughfare ?: ""
                
                when {
                    locality.isNotEmpty() && subLocality.isNotEmpty() -> "$locality $subLocality"
                    adminArea.isNotEmpty() && locality.isNotEmpty() -> "$adminArea $locality"
                    locality.isNotEmpty() -> locality
                    adminArea.isNotEmpty() -> adminArea
                    else -> "위치 파악 완료"
                }
            } else {
                "알 수 없는 위치"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "위치 파악 완료"
        }
    }
}
