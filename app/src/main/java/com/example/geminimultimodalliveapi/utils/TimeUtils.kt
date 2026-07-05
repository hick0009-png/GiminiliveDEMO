package com.example.geminimultimodalliveapi.utils

import java.util.Locale

/**
 * Formats a duration in seconds into a string in "HH:mm:ss" format.
 */
fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
