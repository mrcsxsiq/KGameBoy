package dev.kgameboy.core.serial

import dev.kgameboy.core.interrupt.Interrupts
import kotlin.test.Test
import kotlin.test.assertEquals

class SerialTest {
    private val ints = Interrupts().also { it.flags = 0 }
    private val serial = Serial(ints)

    @Test fun internalClockTransferDeliversByteAndRaisesInterrupt() {
        val out = mutableListOf<Int>()
        serial.onByte = { out += it }
        serial.data = 'A'.code
        serial.writeControl(0x81)
        assertEquals(listOf('A'.code), out)
        assertEquals(1 shl Interrupts.SERIAL, ints.flags and (1 shl Interrupts.SERIAL))
        assertEquals(0, serial.readControl() and 0x80) // transfer flag cleared
    }

    @Test fun externalClockDoesNothing() {
        var called = false
        serial.onByte = { called = true }
        serial.writeControl(0x80)
        assertEquals(false, called)
    }
}
