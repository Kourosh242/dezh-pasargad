package com.pasargad.dezh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * First-letter monogram on a soft container — gives each record a calm,
 * scannable identity without images. RTL-safe: takes the first grapheme.
 */
@Composable
fun EntryMonogram(title: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initial = monogramInitial(title)
    Box(
        modifier = modifier
            .size(size)
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * First grapheme of the title, uppercased — codepoint- and ZWJ-safe so an
 * emoji or a family emoji (surrogate pairs / joined clusters) stays whole
 * (take(1) sliced surrogate pairs into broken glyphs).
 */
internal fun monogramInitial(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return ""
    val initial = StringBuilder()
    var index = 0
    var joined = true
    while (joined && index < trimmed.length) {
        val codePoint = trimmed.codePointAt(index)
        initial.appendCodePoint(codePoint)
        index += Character.charCount(codePoint)
        // A zero-width joiner keeps the cluster going (e.g. 👨‍👩‍👦).
        joined = index < trimmed.length && trimmed.codePointAt(index) == 0x200D
        if (joined) {
            initial.appendCodePoint(0x200D)
            index += 1
        }
    }
    return initial.toString().uppercase()
}
