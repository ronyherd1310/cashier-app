package com.cashierapp.photocheckout.data.recognizer

/**
 * Vendor/model defaults. Lives only in `data/recognizer/` so no model id or vendor
 * string leaks into `domain/` or `ui/` (X-2). Swap the model here (or via config)
 * without touching the rest of the app.
 */
public const val DEFAULT_MODEL_ID: String = "google/gemini-2.0-flash-001"

public const val OPENROUTER_BASE_URL: String = "https://openrouter.ai/api/v1/"
