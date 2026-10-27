package dev.kgameboy.core.interrupt

/** Interrupt flag (IF, 0xFF0F) and enable (IE, 0xFFFF) registers shared by all components. */
class Interrupts {
    var flags = 0xE1
    var enable = 0

    fun request(bit: Int) {
        flags = flags or (1 shl bit)
    }

    /** Bitmask of interrupts that are both requested and enabled. */
    fun pending(): Int = flags and enable and 0x1F

    companion object {
        const val VBLANK = 0
        const val STAT = 1
        const val TIMER = 2
        const val SERIAL = 3
        const val JOYPAD = 4
    }
}
