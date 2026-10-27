package dev.kgameboy.core.audio

/** Channel 3: plays the 32 four-bit samples stored in wave RAM (0xFF30-0xFF3F). */
class WaveChannel {
    var enabled = false; private set
    val ram = IntArray(16)
    private val len = LengthCounter(256)
    private var dac = false
    private var volumeCode = 0
    private var freq = 0
    private var timer = 0
    private var pos = 0

    fun write(reg: Int, v: Int) {
        when (reg) {
            0 -> { dac = v and 0x80 != 0; if (!dac) enabled = false }
            1 -> len.load(v)
            2 -> volumeCode = (v shr 5) and 3
            3 -> freq = (freq and 0x700) or v
            4 -> {
                freq = (freq and 0xFF) or ((v and 7) shl 8)
                len.enabled = v and 0x40 != 0
                if (v and 0x80 != 0) {
                    enabled = dac
                    len.trigger()
                    timer = (2048 - freq) * 2
                    pos = 0
                }
            }
        }
    }

    fun tick(cycles: Int) {
        timer -= cycles
        while (timer <= 0) { timer += (2048 - freq) * 2; pos = (pos + 1) and 31 }
    }

    fun clockLength() { if (len.clock()) enabled = false }
    fun disable() { enabled = false }

    fun output(): Int {
        if (!enabled) return 0
        val byte = ram[pos shr 1]
        val sample = if (pos and 1 == 0) byte shr 4 else byte and 0xF
        return when (volumeCode) { 0 -> 0; 1 -> sample; 2 -> sample shr 1; else -> sample shr 2 }
    }
}
