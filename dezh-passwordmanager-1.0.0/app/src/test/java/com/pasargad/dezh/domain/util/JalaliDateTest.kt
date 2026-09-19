package com.pasargad.dezh.domain.util

import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden-value tests for the jalaali-js port used across the UI. */
class JalaliDateTest {

    private val utc = ZoneOffset.UTC

    private fun millis(y: Int, m: Month, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(utc).toEpochMilli()

    @Test
    fun `today-style date converts to expected jalali`() {
        val j = JalaliDate.fromEpochMillis(millis(2026, Month.SEPTEMBER, 17), utc)
        assertEquals(1405, j.jy)
        assertEquals(6, j.jm)
        assertEquals(26, j.jd)
    }

    @Test
    fun `nowruz conversions are stable`() {
        assertEquals(JalaliDate.Jalali(1405, 1, 1), JalaliDate.fromEpochMillis(millis(2026, Month.MARCH, 21), utc))
        assertEquals(JalaliDate.Jalali(1404, 1, 1), JalaliDate.fromEpochMillis(millis(2025, Month.MARCH, 21), utc))
        assertEquals(JalaliDate.Jalali(1403, 1, 1), JalaliDate.fromEpochMillis(millis(2024, Month.MARCH, 20), utc))
    }

    @Test
    fun `unix epoch is 1348-10-11`() {
        assertEquals(JalaliDate.Jalali(1348, 10, 11), JalaliDate.fromEpochMillis(0L, utc))
    }

    @Test
    fun `format produces persian digits month name and clock`() {
        val text = JalaliDate.formatDateTime(
            millis(2026, Month.SEPTEMBER, 17, 14, 5),
            utc,
            latinDigits = false,
        )
        assertEquals("۲۶ شهریور ۱۴۰۵، ساعت ۱۴:۰۵", text)
    }

    @Test
    fun `format supports latin digits for non-fa locale`() {
        val text = JalaliDate.formatDateTime(
            millis(2026, Month.SEPTEMBER, 17, 14, 5),
            utc,
            latinDigits = true,
        )
        assertEquals("26 شهریور 1405، ساعت 14:05", text)
    }
}
