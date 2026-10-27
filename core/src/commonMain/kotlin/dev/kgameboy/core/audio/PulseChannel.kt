package dev.kgameboy.core.audio

/** Channels 1 and 2: square wave with four duty cycles. Channel 1 also has a frequency sweep. */
class PulseChannel(private val hasSweep: Boolean) {
    var enabled = false; private set
    private val env = Envelope()
    private val len = LengthCounter(64)
    private var duty = 0
    private var freq = 0
    private var timer = 0
    private var step = 0

    private var sweepPeriod = 0; private var sweepNegate = false; private var sweepShift = 0
    private var sweepTimer = 0; private var sweepEnabled = false; private var shadow = 0

    fun write(reg: Int, v: Int) {
        when (reg) {
            0 -> { sweepPeriod = (v shr 4) and 7; sweepNegate = v and 8 != 0; sweepShift = v and 7 }
            1 -> { duty = v shr 6; len.load(v and 0x3F) }
            2 -> { env.write(v); if (!env.dacOn) enabled = false }
            3 -> freq = (freq and 0x700) or v
            4 -> {
                freq = (freq and 0xFF) or ((v and 7) shl 8)
                len.enabled = v and 0x40 != 0
                if (v and 0x80 != 0) trigger()
            }
        }
    }

    private fun trigger() {
        enabled = env.dacOn
        len.trigger()
        timer = (2048 - freq) * 4
        env.trigger()
        if (hasSweep) {
            shadow = freq
            sweepTimer = if (sweepPeriod == 0) 8 else sweepPeriod
            sweepEnabled = sweepPeriod != 0 || sweepShift != 0
            if (sweepShift != 0) sweepCalc()
        }
    }

    private fun sweepCalc(): Int {
        val delta = shadow shr sweepShift
        val next = if (sweepNegate) shadow - delta else shadow + delta
        if (next > 2047) enabled = false
        return next
    }

    fun tick(cycles: Int) {
        timer -= cycles
        while (timer <= 0) { timer += (2048 - freq) * 4; step = (step + 1) and 7 }
    }

    fun clockLength() { if (len.clock()) enabled = false }
    fun clockEnvelope() = env.clock()

    fun clockSweep() {
        if (!hasSweep || --sweepTimer > 0) return
        sweepTimer = if (sweepPeriod == 0) 8 else sweepPeriod
        if (sweepEnabled && sweepPeriod != 0) {
            val next = sweepCalc()
            if (next <= 2047 && sweepShift != 0) { freq = next; shadow = next; sweepCalc() }
        }
    }

    fun disable() { enabled = false }

    /** Current DAC input, 0-15. */
    fun output(): Int = if (enabled && (DUTY[duty] shr (7 - step)) and 1 == 1) env.volume else 0

    private companion object {
        val DUTY = intArrayOf(0b00000001, 0b10000001, 0b10000111, 0b01111110)
    }
}
