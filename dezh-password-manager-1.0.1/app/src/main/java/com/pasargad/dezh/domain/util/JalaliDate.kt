package com.pasargad.dezh.domain.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pure-Kotlin Jalali (Solar Hijri) conversion — port of the widely-used
 * jalaali-js algorithm (valid ~1178–1633 AP), unit-testable on the JVM.
 *
 * The numeric literals in the core functions below are the algorithm's
 * published constants (Julian-day bookkeeping), verified by golden-value
 * unit tests — hence the file-scoped MagicNumber suppression.
 *
 * Used everywhere the UI shows record timestamps, so users never see raw
 * epoch millis (reported bug) or Gregorian-only dates in a Persian UI.
 */
@Suppress("MagicNumber")
object JalaliDate {

    private val MONTHS_FA = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    data class Jalali(val jy: Int, val jm: Int, val jd: Int)

    fun fromEpochMillis(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Jalali {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val (gy, gm, gd) = Triple(date.year, date.monthValue, date.dayOfMonth)
        return d2j(g2d(gy, gm, gd))
    }

    /**
     * Example output (fa digits): «۲۶ شهریور ۱۴۰۵، ساعت ۱۴:۰۵»
     * With latinDigits=true: «26 شهریور 1405، ساعت 14:05»
     */
    fun formatDateTime(
        epochMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        latinDigits: Boolean = Locale.getDefault().language != "fa",
    ): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        val jalali = fromEpochMillis(epochMillis, zone)
        val date = "${toDigits(jalali.jd, latinDigits)} ${MONTHS_FA[jalali.jm - 1]} ${toDigits(jalali.jy, latinDigits)}"
        val hour = toDigits(dateTime.hour, latinDigits, twoDigit = true)
        val minute = toDigits(dateTime.minute, latinDigits, twoDigit = true)
        return "$date، ساعت $hour:$minute"
    }

    private fun toDigits(value: Int, latinDigits: Boolean, twoDigit: Boolean = false): String {
        val raw = if (twoDigit && value < 10) "0$value" else value.toString()
        if (latinDigits) return raw
        return buildString {
            for (ch in raw) append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
        }
    }

    // ── jalaali-js core ──────────────────────────────────────────────────

    /** jalaali-js `div`: Kotlin integer division already truncates toward zero. */
    private fun div(a: Int, b: Int): Int = a / b

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * ((gm + 9) % 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2j(jdn: Int): Jalali {
        val (gy, _, _) = d2g(jdn)
        var jy = gy - 621
        val r = jalCal(jy, false)
        val jdn1f = g2d(gy, 3, r.march)
        var jd: Int
        var jm: Int
        var k = jdn - jdn1f
        when {
            k >= 0 -> {
                if (k <= 185) {
                    jm = 1 + div(k, 31)
                    jd = mod(k, 31) + 1
                    return Jalali(jy, jm, jd)
                } else {
                    k -= 186
                }
            }
            else -> {
                jy -= 1
                k += 179
                if (r.leap == 1) k += 1
            }
        }
        jm = 7 + div(k, 30)
        jd = mod(k, 30) + 1
        return Jalali(jy, jm, jd)
    }

    private fun d2g(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    private fun jalCal(jy: Int, withoutLeap: Boolean): JalCal {
        val breaks = intArrayOf(
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 1701, 1748, 1776, 1784, 1794, 1801, 1831, 1870, 1916, 1948, 2024,
        )
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (!withoutLeap) {
            if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
            var leap = mod(mod(n + 1, 33) - 1, 4)
            if (leap == -1) leap = 4
            return JalCal(leap = leap, march = march)
        }
        return JalCal(leap = 0, march = march)
    }

    private data class JalCal(val leap: Int, val march: Int)

    private fun mod(a: Int, b: Int): Int {
        val r = a % b
        return if (r >= 0) r else r + b
    }
}
