package dev.kgameboy.core.timer

import dev.kgameboy.core.interrupt.Interrupts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimerTest {
    private val ints = Interrupts().also { it.flags = 0 }
    private val timer = Timer(ints).also { it.resetDiv() }

    @Test
    fun divIncrementsEvery256Cycles() {
        timer.tick(255 - 255 % 4)
        assertEquals(0, timer.div)
        timer.tick(256)
        assertEquals(1, timer.div)
    }

    @Test
    fun timaCountsAtSelectedFrequency() {
        timer.tac = 0b101 // enabled, 262144 Hz = every 16 cycles
        timer.tick(16 * 10)
        assertEquals(10, timer.tima)
    }

    @Test
    fun timaStaysStillWhenDisabled() {
        timer.tac = 0b001
        timer.tick(1000)
        assertEquals(0, timer.tima)
    }

    @Test
    fun overflowReloadsTmaAndRequestsInterrupt() {
        timer.tac = 0b101
        timer.tma = 0xAB
        timer.tima = 0xFF
        timer.tick(16)
        assertEquals(0xAB, timer.tima)
        assertTrue(ints.flags and (1 shl Interrupts.TIMER) != 0)
    }

    @Test
    fun writingDivResetsCounter() {
        timer.tick(512)
        timer.resetDiv()
        assertEquals(0, timer.div)
    }
}
