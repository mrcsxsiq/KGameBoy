package dev.kgameboy.core.audio

/**
 * Audio Processing Unit: two pulse channels, a wave channel and a noise channel.
 *
 * A 512 Hz frame sequencer (one step every 8192 T-cycles) clocks the length counters (256 Hz),
 * the channel 1 sweep (128 Hz) and the envelopes (64 Hz). The mixer takes one sample every
 * CPU_HZ / SAMPLE_RATE cycles and appends interleaved stereo PCM to an internal buffer, which
 * [drain] hands to an [AudioSink].
 */
class Apu {
    private val ch1 = PulseChannel(hasSweep = true)
    private val ch2 = PulseChannel(hasSweep = false)
    private val ch3 = WaveChannel()
    private val ch4 = NoiseChannel()

    private val regs = IntArray(0x17) // NR10..NR52 as last written
    private var power = true
    private var sequencerCycles = 0
    private var sequencerStep = 0
    private var sampleAcc = 0

    private val buffer = ShortArray(BUFFER_SAMPLES * 2)
    private var count = 0
    private var capLeft = 0f
    private var capRight = 0f

    init { write(0xFF26, 0x80); write(0xFF24, 0x77); write(0xFF25, 0xF3) }

    fun read(addr: Int): Int {
        if (addr in 0xFF30..0xFF3F) return ch3.ram[addr - 0xFF30]
        if (addr == 0xFF26) {
            val status = (if (ch1.enabled) 1 else 0) or (if (ch2.enabled) 2 else 0) or
                (if (ch3.enabled) 4 else 0) or (if (ch4.enabled) 8 else 0)
            return 0x70 or (if (power) 0x80 else 0) or status
        }
        val i = addr - 0xFF10
        return if (i in READ_MASKS.indices) regs[i] or READ_MASKS[i] else 0xFF
    }

    fun write(addr: Int, v: Int) {
        if (addr in 0xFF30..0xFF3F) { ch3.ram[addr - 0xFF30] = v; return }
        if (addr == 0xFF26) { setPower(v and 0x80 != 0); return }
        if (!power || addr !in 0xFF10..0xFF25) return
        regs[addr - 0xFF10] = v
        when (addr) {
            in 0xFF10..0xFF14 -> ch1.write(addr - 0xFF10, v)
            in 0xFF16..0xFF19 -> ch2.write(addr - 0xFF15, v)
            in 0xFF1A..0xFF1E -> ch3.write(addr - 0xFF1A, v)
            in 0xFF20..0xFF23 -> ch4.write(addr - 0xFF1F, v)
        }
    }

    private fun setPower(on: Boolean) {
        if (!on && power) {
            regs.fill(0)
            ch1.disable(); ch2.disable(); ch3.disable(); ch4.disable()
            // Register writes are ignored while off, so reset channels by writing zeros first.
            for (r in 0..4) { ch1.write(r, 0); ch2.write(r, 0); ch3.write(r, 0) }
            for (r in 1..4) ch4.write(r, 0)
        }
        if (on && !power) sequencerStep = 0
        power = on
    }

    fun tick(cycles: Int) {
        var left = cycles
        while (left > 0) {
            left -= 4
            if (power) {
                sequencerCycles += 4
                if (sequencerCycles >= 8192) { sequencerCycles -= 8192; stepSequencer() }
                ch1.tick(4); ch2.tick(4); ch3.tick(4); ch4.tick(4)
            }
            sampleAcc += 4 * SAMPLE_RATE
            if (sampleAcc >= CPU_HZ) { sampleAcc -= CPU_HZ; mix() }
        }
    }

    private fun stepSequencer() {
        val s = sequencerStep
        if (s and 1 == 0) { ch1.clockLength(); ch2.clockLength(); ch3.clockLength(); ch4.clockLength() }
        if (s == 2 || s == 6) ch1.clockSweep()
        if (s == 7) { ch1.clockEnvelope(); ch2.clockEnvelope(); ch4.clockEnvelope() }
        sequencerStep = (s + 1) and 7
    }

    private fun mix() {
        if (count >= BUFFER_SAMPLES) return // consumer is behind: drop instead of growing
        var l = 0f; var r = 0f
        if (power) {
            val outs = intArrayOf(ch1.output(), ch2.output(), ch3.output(), ch4.output())
            val pan = regs[0x15]
            val vol = regs[0x14]
            for (i in 0 until 4) {
                if (pan and (0x10 shl i) != 0) l += outs[i]
                if (pan and (1 shl i) != 0) r += outs[i]
            }
            l = l / 60f * (((vol shr 4) and 7) + 1) / 8f
            r = r / 60f * ((vol and 7) + 1) / 8f
        }
        // One-pole high-pass filter removes the DC offset of the unsigned DAC output.
        val outL = l - capLeft; capLeft = l - outL * CHARGE
        val outR = r - capRight; capRight = r - outR * CHARGE
        buffer[count * 2] = (outL * 28000f).coerceIn(-32768f, 32767f).toInt().toShort()
        buffer[count * 2 + 1] = (outR * 28000f).coerceIn(-32768f, 32767f).toInt().toShort()
        count++
    }

    /** Sends the samples produced since the last call to [sink]. */
    fun drain(sink: AudioSink) {
        if (count > 0) sink.push(buffer, count * 2)
        count = 0
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val CPU_HZ = 4194304
        private const val BUFFER_SAMPLES = 4096
        private const val CHARGE = 0.996f

        /** Bits that always read as 1 for NR10..NR25 (write-only or unused bits). */
        private val READ_MASKS = intArrayOf(
            0x80, 0x3F, 0x00, 0xFF, 0xBF,
            0xFF, 0x3F, 0x00, 0xFF, 0xBF,
            0x7F, 0xFF, 0x9F, 0xFF, 0xBF,
            0xFF, 0xFF, 0x00, 0x00, 0xBF,
            0x00, 0x00,
        )
    }
}
