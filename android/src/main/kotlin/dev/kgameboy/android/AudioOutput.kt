package dev.kgameboy.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dev.kgameboy.core.audio.Apu
import dev.kgameboy.core.audio.AudioSink

/**
 * Streams samples to an AudioTrack. The blocking write paces the emulator: about 738 samples per frame
 * at 44100 Hz is exactly 59.73 frames per second.
 */
class AudioOutput : AudioSink {
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Apu.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build(),
        )
        .setBufferSizeInBytes(4 * 1024 * 4)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
        .also { it.play() }

    override fun push(samples: ShortArray, count: Int) {
        track.write(samples, 0, count)
    }

    fun release() { track.stop(); track.release() }
}
