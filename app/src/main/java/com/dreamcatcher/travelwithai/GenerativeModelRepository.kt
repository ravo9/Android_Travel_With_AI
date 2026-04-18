package com.dreamcatcher.travelwithai

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.content

object ModelNames {
    const val DEFAULT_GENERATION = "gemini-2.5-flash"
    const val GEMINI_1_5_FLASH = "gemini-1.5-flash"
}

class GenerativeModelRepository() {
    private var generativeModel: GenerativeModel? = null

    fun initializeModel(apiKey: String) {
        generativeModel = GenerativeModel(
            modelName = ModelNames.DEFAULT_GENERATION,
            apiKey = apiKey
        )
    }

    suspend fun generateResponse(prompt: String, image: Bitmap? = null): String? {
        val model = generativeModel
        if (model == null) {
            Log.e(TAG, "Generative model not initialized")
            return null
        }
        return try {
            val response = model.generateContent(content {
                text(prompt)
                image?.let { image(it) }
            })
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "generateContent failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "TravelWithAI.Gemini"
    }
}
