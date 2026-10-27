package dev.kgameboy.core.serial

import dev.kgameboy.core.interrupt.Interrupts

/**
 * Serial port (SB 0xFF01, SC 0xFF02). With no link cable attached, a transfer started with the
 * internal clock simply hands the byte to [onByte] and completes at once. Test ROMs print their
 * results this way.
 */
class Serial(private val interrupts: Interrupts) {
    var data = 0
    private var control = 0
    var onByte: ((Int) -> Unit)? = null

    fun readControl() = control or 0x7E

    fun writeControl(value: Int) {
        control = value and 0x81
        if (control == 0x81) { // transfer requested, internal clock
            onByte?.invoke(data)
            data = 0xFF // nothing is plugged in
            control = control and 0x7F
            interrupts.request(Interrupts.SERIAL)
        }
    }
}
