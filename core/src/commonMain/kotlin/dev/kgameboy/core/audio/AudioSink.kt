package dev.kgameboy.core.audio

/** Receives interleaved stereo 16-bit samples at [Apu.SAMPLE_RATE] Hz. */
fun interface AudioSink {
    fun push(samples: ShortArray, count: Int)
}
