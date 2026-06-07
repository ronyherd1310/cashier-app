package com.cashierapp.photocheckout.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cashierapp.photocheckout.data.recognizer.DEFAULT_MODEL_ID
import com.cashierapp.photocheckout.domain.recognizer.CONFIDENCE_THRESHOLD
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider configuration stored in a single EncryptedSharedPreferences file: the
 * OpenRouter API key, the model id, and an optional confidence-threshold override.
 * The key is never logged and never a literal; callers use the typed surface and
 * never touch raw pref keys (Boundaries, X-2).
 */
@Singleton
public class RecognizerConfig
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences by lazy {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        public val apiKey: String?
            get() = prefs.getString(KEY_API_KEY, null)

        public val modelId: String
            get() = prefs.getString(KEY_MODEL_ID, null) ?: DEFAULT_MODEL_ID

        public val confidenceThreshold: Float
            get() =
                if (prefs.contains(KEY_CONFIDENCE_THRESHOLD)) {
                    prefs.getFloat(KEY_CONFIDENCE_THRESHOLD, CONFIDENCE_THRESHOLD)
                } else {
                    CONFIDENCE_THRESHOLD
                }

        public fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

        public fun setApiKey(value: String?) {
            prefs
                .edit()
                .apply {
                    if (value.isNullOrBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, value)
                }.apply()
        }

        public fun setModelId(value: String?) {
            prefs
                .edit()
                .apply {
                    if (value.isNullOrBlank()) remove(KEY_MODEL_ID) else putString(KEY_MODEL_ID, value)
                }.apply()
        }

        public fun setConfidenceThreshold(value: Float?) {
            prefs
                .edit()
                .apply {
                    if (value == null) remove(KEY_CONFIDENCE_THRESHOLD) else putFloat(KEY_CONFIDENCE_THRESHOLD, value)
                }.apply()
        }

        private companion object {
            const val PREFS_FILE_NAME = "recognizer_config"
            const val KEY_API_KEY = "openrouter_api_key"
            const val KEY_MODEL_ID = "openrouter_model_id"
            const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        }
    }
