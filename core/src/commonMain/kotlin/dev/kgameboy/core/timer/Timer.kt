package dev.kgameboy.core.timer

import dev.kgameboy.core.interrupt.Interrupts

/**
 * DIV/TIMA/TMA/TAC. DIV is the top byte of a free-running 16-bit counter; TIMA increments on
 * the falling edge of one bit of that counter, selected by TAC.
 */
class Timer(private val interrupts: Interrupts) {
    var counter = 0xABCC // internal 16-bit counter (post-boot value)
        private set
    var tima = 0
    var tma = 0
    var tac = 0

    val div: Int get() = counter ushr 8

    fun resetDiv() {
        val old = counter
        counter = 0
        checkEdge(old, counter)
    }

    fun tick(cycles: Int) {
        var left = cycles
        while (left > 0) {
            val old = counter
            counter = (counter + 4) and 0xFFFF
            checkEdge(old, counter)
            left -= 4
        }
    }

    private fun checkEdge(old: Int, new: Int) {
        if (tac and 0x04 == 0) return
        val mask = BITS[tac and 3]
        if (old and mask != 0 && new and mask == 0) {
            tima++
            if (tima > 0xFF) {
                tima = tma
                interrupts.request(Interrupts.TIMER)
            }
        }
    }

    private companion object {
        // TAC clock select -> counter bit: 4096 Hz, 262144 Hz, 65536 Hz, 16384 Hz
        val BITS = intArrayOf(1 shl 9, 1 shl 3, 1 shl 5, 1 shl 7)
    }
}
