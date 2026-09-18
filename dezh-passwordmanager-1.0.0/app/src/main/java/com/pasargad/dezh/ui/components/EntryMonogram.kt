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
    val initial = title.trim().take(1).uppercase()
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
