package com.cashierapp.photocheckout.data.recognizer

/**
 * Vendor/model defaults. Lives only in `data/recognizer/` so no model id or vendor
 * string leaks into `domain/` or `ui/` (X-2). Swap the model here (or via config)
 * without touching the rest of the app.
 */
public const val DEFAULT_MODEL_ID: String = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free"

public const val OPENROUTER_BASE_URL: String = "https://openrouter.ai/api/v1/"

public const val REFERENCE_PHOTO_SKU_CAP: Int = 30

public const val CROP_PADDING_FRACTION: Float = 0.1f
