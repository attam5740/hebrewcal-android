package com.hebrewcal.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LatLng(val latitude: Double, val longitude: Double)

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLng = suspendCancellableCoroutine { cont ->
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Return a cached fix immediately if one is available. Only coarse-compatible
        // providers are queried — GPS_PROVIDER requires ACCESS_FINE_LOCATION, which the
        // app no longer holds. City-level accuracy is enough (the fix is snapped to the
        // nearest city in CityDatabase).
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            val last = try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
            if (last != null) {
                cont.resume(LatLng(last.latitude, last.longitude))
                return@suspendCancellableCoroutine
            }
        }

        // No cached fix — request a single fresh update from the network provider
        // (coarse-compatible). GPS is intentionally not requested.
        val enabledProvider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resumeWithException(Exception("No location provider available"))
                return@suspendCancellableCoroutine
            }
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                if (cont.isActive) cont.resume(LatLng(location.latitude, location.longitude))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String, status: Int, extras: android.os.Bundle?) = Unit
            override fun onProviderDisabled(provider: String) {
                lm.removeUpdates(this)
                if (cont.isActive) cont.resumeWithException(Exception("Location provider disabled"))
            }
        }

        try {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(enabledProvider, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            cont.resumeWithException(e)
        }

        cont.invokeOnCancellation { lm.removeUpdates(listener) }
    }
}
