package com.dreamcatcher.travelwithai

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.google.firebase.ktx.Firebase

class RemoteConfigRepository {
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    init {
        val configSettings = remoteConfigSettings { minimumFetchIntervalInSeconds = 3600 }
        remoteConfig.setConfigSettingsAsync(configSettings)
    }

    fun fetchApiKey(onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess(remoteConfig.getString("api_key"))
                } else {
                    val err = task.exception ?: Throwable("fetchAndActivate failed with no exception")
                    Log.e(TAG, "Remote Config fetchAndActivate failed", err)
                    onError(err)
                }
            }
    }

    companion object {
        private const val TAG = "TravelWithAI.RemoteConfig"
    }
}
