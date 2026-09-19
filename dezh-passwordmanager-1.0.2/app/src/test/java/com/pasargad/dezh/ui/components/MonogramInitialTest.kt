package com.pasargad.dezh.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.0.2 bug hunt: the monogram must take a full grapheme, not half a surrogate pair. */
class MonogramInitialTest {

    @Test
    fun latin_first_letter_uppercased() {
        assertEquals("G", monogramInitial("Gmail"))
        assertEquals("B", monogramInitial("  bank "))
    }

    @Test
    fun persian_first_letter() {
        assertEquals("ب", monogramInitial("بانک ملت"))
    }

    @Test
    fun emoji_is_kept_whole() {
        // Rocket is a surrogate pair (2 chars); take(1) produced a broken glyph.
        assertEquals("🚀", monogramInitial("🚀پرتاب"))
        assertEquals("👨‍👩‍👦", monogramInitial("👨‍👩‍👦خانواده"))
    }

    @Test
    fun blank_title_yields_empty_initial() {
        assertEquals("", monogramInitial("   "))
        assertEquals("", monogramInitial(""))
    }
}
