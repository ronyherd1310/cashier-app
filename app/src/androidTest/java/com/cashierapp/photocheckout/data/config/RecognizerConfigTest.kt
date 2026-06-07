package com.cashierapp.photocheckout.data.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.recognizer.DEFAULT_MODEL_ID
import com.cashierapp.photocheckout.domain.recognizer.CONFIDENCE_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

public class RecognizerConfigTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    public fun clearPrefs() {
        context.deleteSharedPreferences("recognizer_config")
    }

    @Test
    public fun defaultsWhenUnset() {
        val config = RecognizerConfig(context)
        assertNull(config.apiKey)
        assertFalse(config.hasApiKey())
        assertEquals(DEFAULT_MODEL_ID, config.modelId)
        assertEquals(CONFIDENCE_THRESHOLD, config.confidenceThreshold)
    }

    @Test
    public fun writesAndReadsTypedValues() {
        val config = RecognizerConfig(context)
        config.setApiKey("sk-or-secret")
        config.setModelId("openai/gpt-4o-mini")
        config.setConfidenceThreshold(0.75f)

        // a fresh instance reads back the persisted values
        val reopened = RecognizerConfig(context)
        assertEquals("sk-or-secret", reopened.apiKey)
        assertTrue(reopened.hasApiKey())
        assertEquals("openai/gpt-4o-mini", reopened.modelId)
        assertEquals(0.75f, reopened.confidenceThreshold)
    }

    @Test
    public fun blankApiKeyClearsIt() {
        val config = RecognizerConfig(context)
        config.setApiKey("sk-or-secret")
        config.setApiKey("")
        assertFalse(config.hasApiKey())
    }
}
