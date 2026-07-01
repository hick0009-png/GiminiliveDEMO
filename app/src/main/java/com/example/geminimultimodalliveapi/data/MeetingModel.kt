package com.example.geminimultimodalliveapi.data

import kotlinx.serialization.Serializable

@Serializable
data class TranscriptSegment(
    var speaker: String,
    val text: String
)

data class Meeting(
    val id: String,
    val title: String,
    val timestamp: Long,
    val duration: Long, // in seconds
    val filePath: String,
    val summary: String?,
    val transcriptJson: String?
)
