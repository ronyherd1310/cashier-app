package com.cashierapp.photocheckout.domain.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

public class SkuGeneratorTest {
    @Test
    public fun generatesFixedPrefixAndFourDigitSequence() {
        assertEquals("SKU-0001", SkuGenerator.generate(1))
        assertEquals("SKU-0042", SkuGenerator.generate(42))
        assertEquals("SKU-9999", SkuGenerator.generate(9_999))
    }

    @Test
    public fun supportsSequencesBeyondFourDigitsWithoutTruncation() {
        assertEquals("SKU-10000", SkuGenerator.generate(10_000))
    }

    @Test
    public fun rejectsNonPositiveSequenceNumbers() {
        assertThrows(IllegalArgumentException::class.java) { SkuGenerator.generate(0) }
        assertThrows(IllegalArgumentException::class.java) { SkuGenerator.generate(-1) }
    }
}
