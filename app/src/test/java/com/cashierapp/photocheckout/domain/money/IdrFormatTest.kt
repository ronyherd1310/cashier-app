package com.cashierapp.photocheckout.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

public class IdrFormatTest {
    @Test
    public fun formatsRupiahWithDotThousandsAndNoDecimals() {
        assertEquals("0", IdrFormat.format(0L))
        assertEquals("25.000", IdrFormat.format(25_000L))
        assertEquals("1.234.567", IdrFormat.format(1_234_567L))
    }

    @Test
    public fun parsesFormattedRupiahIntoMinorUnits() {
        assertEquals(25_000L, IdrFormat.parse("25.000"))
        assertEquals(1_234_567L, IdrFormat.parse("1.234.567"))
        assertEquals(15_000L, IdrFormat.parse("IDR 15.000"))
    }

    @Test
    public fun rejectsInvalidMoneyInput() {
        assertThrows(IllegalArgumentException::class.java) { IdrFormat.parse("-1") }
        assertThrows(IllegalArgumentException::class.java) { IdrFormat.parse("25.000,50") }
        assertThrows(IllegalArgumentException::class.java) { IdrFormat.parse("abc") }
        assertThrows(IllegalArgumentException::class.java) { IdrFormat.parse("") }
    }

    @Test
    public fun roundTripsRepresentativeValues() {
        val values = listOf(0L, 1L, 999L, 1_000L, 25_000L, 999_999_999L)

        values.forEach { value ->
            assertEquals(value, IdrFormat.parse(IdrFormat.format(value)))
        }
    }
}
