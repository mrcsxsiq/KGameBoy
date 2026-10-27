package dev.kgameboy.core.input

import dev.kgameboy.core.interrupt.Interrupts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoypadTest {
    private val ints = Interrupts().also { it.flags = 0 }
    private val pad = Joypad(ints)

    @Test
    fun nothingPressedReadsAllOnes() {
        pad.write(0x20) // select directions
        assertEquals(0x0F, pad.read() and 0x0F)
    }

    @Test
    fun pressedKeyReadsZeroOnlyInSelectedRow() {
        pad.set(Button.RIGHT, true)
        pad.write(0x20) // directions selected (bit 4 low)
        assertEquals(0b1110, pad.read() and 0x0F)
        pad.write(0x10) // actions selected
        assertEquals(0x0F, pad.read() and 0x0F)
    }

    @Test
    fun actionButtonsAreInTheirOwnRow() {
        pad.set(Button.START, true)
        pad.write(0x10)
        assertEquals(0b0111, pad.read() and 0x0F)
    }

    @Test
    fun pressingSelectedKeyRaisesInterrupt() {
        pad.write(0x10)
        pad.set(Button.A, true)
        assertTrue(ints.flags and (1 shl Interrupts.JOYPAD) != 0)
    }

    @Test
    fun pressingUnselectedKeyDoesNotRaiseInterrupt() {
        pad.write(0x20) // directions only
        pad.set(Button.A, true)
        assertEquals(0, ints.flags and (1 shl Interrupts.JOYPAD))
    }
}
