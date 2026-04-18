package com.dreamcatcher.travelwithai

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.text.replace

class MainViewModel(
    private val locationRepository: LocationRepository,
    private val imagesRepository: ImagesRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val generativeModelRepository: GenerativeModelRepository,
) : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _location: MutableStateFlow<String> = MutableStateFlow("Looking for your physical location by GPS...")
    val location: StateFlow<String> = _location.asStateFlow()

    init { initializeGenerativeModel() }

    fun userAgreedLocation() {
        viewModelScope.launch {
            val location = locationRepository.getCurrentLocation()
            if (location == null) {
                _location.value = "Last location not found."
            } else {
                _location.value = location.toDetailedString()
            }
        }
    }

    fun userDeniedLocation() {
        _location.value = "Please check your location permissions."
    }

    private fun initializeGenerativeModel() {
        remoteConfigRepository.fetchApiKey(
            onSuccess = { apiKey -> generativeModelRepository.initializeModel(apiKey) },
            onError = { _uiState.value = UiState.Error("Problem with the server.") },
        )
    }

    fun getAIGeneratedImages(): Array<Int> {
        return imagesRepository.getAIGeneratedImages()
    }

    fun sendPrompt(
        messageType: MessageType,
        prompt: String? = null,
        photo: Bitmap? = null,
        manualLocation: String? = null
    ) {
        _uiState.value = UiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val location = manualLocation ?: locationRepository.getCurrentLocation()
                // Todo: Update location in UI in this place.
//                val location = locationRepository.getFakeLocation() // For screenshots
                if (location == null) {
                    _uiState.value = UiState.Error("Location not found.")
                    return@launch
                }
                if (messageType == MessageType.PHOTO && photo == null) {
                    _uiState.value = UiState.Error("Picture taking error.")
                    return@launch
                }
                val enhancedPrompt = if (manualLocation != null) {
                    enhancePrompt(messageType, location as String, prompt)
                } else {
                    enhancePrompt(messageType, location as Location, prompt)
                }
                val response = try {
                    withTimeout(GEMINI_CALL_TIMEOUT_MS) {
                        generativeModelRepository.generateResponse(enhancedPrompt, photo)
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Gemini request timed out", e)
                    _uiState.value =
                        UiState.Error("Request timed out. Check your network and try again.")
                    return@launch
                }
                if (response != null) _uiState.value = UiState.Success(cleanResponse(response))
                else _uiState.value = UiState.Error("Error (received prompt is empty).")
            } catch (e: Exception) {
                Log.e(TAG, "sendPrompt failed", e)
                _uiState.value = UiState.Error(e.localizedMessage ?: "Sending prompt error.")
            }
        }
    }

    private fun enhancePrompt(messageType: MessageType, location: Location, prompt: String?) =
        messageType.getMessage(location, prompt ?: "")

    private fun enhancePrompt(messageType: MessageType, location: String, prompt: String?) =
        messageType.getMessage(location, prompt ?: "")

    private fun cleanResponse(response: String) = response.replace("**", "")

    companion object {
        private const val TAG = "TravelWithAI.MainVM"
        private const val GEMINI_CALL_TIMEOUT_MS = 120_000L
    }

    fun Location.toDetailedString(): String {
        return buildString {
            append("• Latitude: %.4f\n".format(latitude))
            append("• Longitude: %.4f\n".format(longitude))
            if (hasAltitude()) append("• Altitude: %.2f meters\n".format(altitude))
            if (hasAccuracy()) append("• Accuracy: %.2f meters".format(accuracy))
        }
    }
}

class BakingViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(
                LocationRepository(context),
                ImagesRepository(),
                RemoteConfigRepository(),
                GenerativeModelRepository(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
