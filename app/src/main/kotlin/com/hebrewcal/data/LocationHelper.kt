package com.hebrewcal.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LatLng(val latitude: Double, val longitude: Double)

class LocationHelper(private val context: Context) {

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLng = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(LatLng(location.latitude, location.longitude))
                } else {
                    // getCurrentLocation returns null when no recent fix is cached;
                    // fall back to lastLocation before giving up.
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) cont.resume(LatLng(last.latitude, last.longitude))
                            else cont.resumeWithException(Exception("Location unavailable"))
                        }
                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                }
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
        cont.invokeOnCancellation { cts.cancel() }
    }
}
