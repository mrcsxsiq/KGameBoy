package dev.kgameboy.core.audio

/** Volume envelope (NRx2): the volume steps up or down by one every `period` ticks of 64 Hz. */
class Envelope {
    var initial = 0; private var add = false; private var period = 0; private var timer = 0
    var volume = 0; private set
    /** The channel's DAC is on when the top five bits of NRx2 are not all zero. */
    var dacOn = false; private set

    fun write(v: Int) {
        initial = v shr 4; add = v and 8 != 0; period = v and 7
        dacOn = v and 0xF8 != 0
    }

    fun trigger() { volume = initial; timer = if (period == 0) 8 else period }

    fun clock() {
        if (period == 0) return
        if (--timer > 0) return
        timer = period
        if (add && volume < 15) volume++ else if (!add && volume > 0) volume--
    }
}

/** Length counter: silences the channel after `max - loaded` ticks of 256 Hz when enabled. */
class LengthCounter(private val max: Int) {
    private var counter = 0
    var enabled = false

    fun load(v: Int) { counter = max - v }
    fun trigger() { if (counter == 0) counter = max }

    /** Returns true when the channel must be switched off. */
    fun clock(): Boolean {
        if (!enabled || counter == 0) return false
        return --counter == 0
    }
}
