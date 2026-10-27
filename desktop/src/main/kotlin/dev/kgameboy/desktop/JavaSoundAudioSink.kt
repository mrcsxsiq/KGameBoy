package dev.kgameboy.desktop

import dev.kgameboy.core.audio.Apu
import dev.kgameboy.core.audio.AudioSink
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * Plays samples through the default output device. The write blocks when the device buffer is
 * full, which makes the audio clock the pace-maker of the emulator: about 738 samples per frame at
 * 44100 Hz is exactly 59.73 frames per second.
 */
class JavaSoundAudioSink : AudioSink, AutoCloseable {
    private val line: SourceDataLine? = try {
        val format = AudioFormat(Apu.SAMPLE_RATE.toFloat(), 16, 2, true, false)
        AudioSystem.getSourceDataLine(format).also { it.open(format, 4 * 1024 * 4); it.start() }
    } catch (e: Exception) {
        System.err.println("Audio unavailable: ${e.message}")
        null
    }

    val available: Boolean get() = line != null
    private var bytes = ByteArray(0)

    override fun push(samples: ShortArray, count: Int) {
        val l = line ?: return
        if (bytes.size < count * 2) bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            bytes[i * 2] = samples[i].toInt().toByte()
            bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        l.write(bytes, 0, count * 2)
    }

    /** Drops queued samples, e.g. when switching ROMs. */
    fun flush() { line?.flush() }

    override fun close() { line?.stop(); line?.close() }
}
