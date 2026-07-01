package com.example.geminimultimodalliveapi.audio

import android.media.AudioFormat

object AudioConfig {
    const val INPUT_SAMPLE_RATE = 16000
    const val OUTPUT_SAMPLE_RATE = 24000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
}
