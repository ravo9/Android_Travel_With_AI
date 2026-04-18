package com.dreamcatcher.travelwithai

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class LocationRepository(context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            withTimeout(LOCATION_REQUEST_TIMEOUT_MS) {
                getCurrentLocationImpl()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Location request timed out.", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationImpl(): Location? = suspendCancellableCoroutine { continuation ->
        val cancel = CancellationTokenSource()
        val finished = AtomicBoolean(false)
        continuation.invokeOnCancellation { cancel.cancel() }

        fun finish(location: Location?) {
            if (!finished.compareAndSet(false, true)) return
            if (continuation.isActive) {
                continuation.resume(location)
            }
        }

        fun requestFreshLocation() {
            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancel.token,
                )
                .addOnSuccessListener { fresh -> finish(fresh) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "getCurrentLocation failed", e)
                    finish(null)
                }
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    finish(cached)
                } else {
                    requestFreshLocation()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "lastLocation failed", e)
                requestFreshLocation()
            }
    }

    companion object {
        private const val TAG = "TravelWithAI.Location"
        private const val LOCATION_REQUEST_TIMEOUT_MS = 45_000L
    }

    // For testing only
    suspend fun getFakeLocation(): Location? {

        val edinburghLocation = Location("provider").apply {
            latitude = 55.9701 // Edinburgh, Leith
            longitude = -3.1894
        }

        val londonLocation = Location("provider").apply {
            latitude = 51.5134  // London, near St. Paul's Cathedral
            longitude = -0.0985  // Longitude near St. Paul's Cathedral, over the River Thames
        }

        val wroclawLocation = Location("provider").apply {
            latitude = 51.1080 // Wroclaw, Hiszpanska street
            longitude = 17.0310
        }

        val bangkokLocation = Location("provider").apply {
            latitude = 13.7367 // Bangkok, SuunCity Condo
            longitude = 100.5339
        }

        val location = londonLocation
        return suspendCancellableCoroutine { continuation ->
            continuation.resume(location) {}
        }
    }
}
