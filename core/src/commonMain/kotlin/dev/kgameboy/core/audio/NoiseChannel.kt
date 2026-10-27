package dev.kgameboy.core.audio

/** Channel 4: pseudo-random noise from a 15-bit linear feedback shift register. */
class NoiseChannel {
    var enabled = false; private set
    private val env = Envelope()
    private val len = LengthCounter(64)
    private var shift = 0; private var narrow = false; private var divisor = 0
    private var timer = 0
    private var lfsr = 0x7FFF

    private fun period() = DIVISORS[divisor] shl shift

    fun write(reg: Int, v: Int) {
        when (reg) {
            1 -> len.load(v and 0x3F)
            2 -> { env.write(v); if (!env.dacOn) enabled = false }
            3 -> { shift = v shr 4; narrow = v and 8 != 0; divisor = v and 7 }
            4 -> {
                len.enabled = v and 0x40 != 0
                if (v and 0x80 != 0) {
                    enabled = env.dacOn
                    len.trigger()
                    timer = period()
                    lfsr = 0x7FFF
                    env.trigger()
                }
            }
        }
    }

    fun tick(cycles: Int) {
        timer -= cycles
        while (timer <= 0) {
            timer += period()
            val x = (lfsr and 1) xor ((lfsr shr 1) and 1)
            lfsr = (lfsr shr 1) or (x shl 14)
            if (narrow) lfsr = (lfsr and 0x40.inv()) or (x shl 6)
        }
    }

    fun clockLength() { if (len.clock()) enabled = false }
    fun clockEnvelope() = env.clock()
    fun disable() { enabled = false }

    fun output(): Int = if (enabled && lfsr and 1 == 0) env.volume else 0

    private companion object {
        val DIVISORS = intArrayOf(8, 16, 32, 48, 64, 80, 96, 112)
    }
}
