package com.pasargad.dezh.presentation.update

import java.util.Locale

/** Human-readable byte size (kept in Latin digits, like the version display). */
internal fun formatBytes(bytes: Long): String = when {
    bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes / BYTES_PER_MB)
    bytes >= BYTES_PER_KB -> String.format(Locale.US, "%.0f KB", bytes / BYTES_PER_KB)
    else -> String.format(Locale.US, "%d B", bytes)
}

private const val BYTES_PER_KB = 1024f
private const val BYTES_PER_MB = 1024f * 1024f
