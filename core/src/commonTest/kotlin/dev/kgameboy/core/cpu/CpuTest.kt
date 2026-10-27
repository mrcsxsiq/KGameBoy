package dev.kgameboy.core.cpu

import dev.kgameboy.core.interrupt.Interrupts
import dev.kgameboy.core.memory.Bus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CpuTest {
    private class FlatBus : Bus {
        val mem = IntArray(0x10000)
        override fun read(addr: Int) = mem[addr]
        override fun write(addr: Int, value: Int) { mem[addr] = value and 0xFF }
    }

    private val bus = FlatBus()
    private val ints = Interrupts().also { it.flags = 0 }
    private val cpu = Cpu(bus, ints).also { it.pc = 0x100; it.f = 0; it.sp = 0xFFFE }

    /** Loads [bytes] at the reset vector and runs [steps] instructions; returns total cycles. */
    private fun run(vararg bytes: Int, steps: Int = 1): Int {
        bytes.forEachIndexed { i, v -> bus.mem[0x100 + i] = v }
        return (1..steps).sumOf { cpu.step() }
    }

    private fun flags(z: Boolean, n: Boolean, h: Boolean, c: Boolean) =
        (if (z) 0x80 else 0) or (if (n) 0x40 else 0) or (if (h) 0x20 else 0) or (if (c) 0x10 else 0)

    @Test fun nopTakesFourCycles() { assertEquals(4, run(0x00)); assertEquals(0x101, cpu.pc) }

    @Test fun loadImmediate() { assertEquals(8, run(0x3E, 0x42)); assertEquals(0x42, cpu.a) }

    @Test fun load16Immediate() { assertEquals(12, run(0x01, 0x34, 0x12)); assertEquals(0x1234, cpu.bc) }

    @Test fun registerToRegisterLoad() { cpu.b = 7; run(0x78); assertEquals(7, cpu.a) }

    @Test fun loadThroughHl() {
        cpu.hl = 0xC000; cpu.a = 0x99
        assertEquals(8, run(0x77)); assertEquals(0x99, bus.mem[0xC000])
    }

    @Test fun addSetsHalfCarryAndCarry() {
        cpu.a = 0xFF; cpu.b = 0x01
        run(0x80)
        assertEquals(0, cpu.a); assertEquals(flags(true, false, true, true), cpu.f)
    }

    @Test fun subSetsNegativeAndBorrow() {
        cpu.a = 0x00; cpu.b = 0x01
        run(0x90)
        assertEquals(0xFF, cpu.a); assertEquals(flags(false, true, true, true), cpu.f)
    }

    @Test fun adcUsesCarry() {
        cpu.a = 0x0F; cpu.b = 0x00; cpu.f = Cpu.C
        run(0x88)
        assertEquals(0x10, cpu.a); assertTrue(cpu.f and Cpu.H != 0)
    }

    @Test fun sbcUsesCarry() {
        cpu.a = 0x10; cpu.b = 0x0F; cpu.f = Cpu.C
        run(0x98)
        assertEquals(0x00, cpu.a); assertTrue(cpu.f and Cpu.Z != 0)
    }

    @Test fun andSetsHalfCarry() { cpu.a = 0xF0; cpu.b = 0x0F; run(0xA0); assertEquals(flags(true, false, true, false), cpu.f) }

    @Test fun xorSelfClearsAAndSetsZ() { cpu.a = 0x55; run(0xAF); assertEquals(0, cpu.a); assertEquals(Cpu.Z, cpu.f) }

    @Test fun cpDoesNotChangeA() { cpu.a = 5; run(0xFE, 5); assertEquals(5, cpu.a); assertTrue(cpu.f and Cpu.Z != 0) }

    @Test fun incKeepsCarryAndSetsHalfCarry() {
        cpu.b = 0x0F; cpu.f = Cpu.C
        run(0x04)
        assertEquals(0x10, cpu.b); assertEquals(flags(false, false, true, true), cpu.f)
    }

    @Test fun decSetsNegativeAndZero() { cpu.b = 1; run(0x05); assertEquals(flags(true, true, false, false), cpu.f) }

    @Test fun incHlTakesTwelveCycles() { cpu.hl = 0xC000; assertEquals(12, run(0x34)); assertEquals(1, bus.mem[0xC000]) }

    @Test fun add16SetsHalfCarryAtBit11() {
        cpu.hl = 0x0FFF; cpu.bc = 1
        run(0x09)
        assertEquals(0x1000, cpu.hl); assertTrue(cpu.f and Cpu.H != 0); assertFalse(cpu.f and Cpu.C != 0)
    }

    @Test fun incDec16DoNotTouchFlags() { cpu.bc = 0xFFFF; cpu.f = 0xF0; run(0x03); assertEquals(0, cpu.bc); assertEquals(0xF0, cpu.f) }

    @Test fun pushAndPop() {
        cpu.bc = 0xBEEF
        run(0xC5, 0xD1, steps = 2)
        assertEquals(0xBEEF, cpu.de); assertEquals(0xFFFE, cpu.sp)
    }

    @Test fun popAfMasksLowNibbleOfFlags() { bus.mem[0xFFFC] = 0xFF; bus.mem[0xFFFD] = 0x12; cpu.sp = 0xFFFC; run(0xF1); assertEquals(0xF0, cpu.f) }

    @Test fun callAndRet() {
        assertEquals(24, run(0xCD, 0x00, 0x20)) // CALL 0x2000
        assertEquals(0x2000, cpu.pc)
        bus.mem[0x2000] = 0xC9
        assertEquals(16, cpu.step()); assertEquals(0x103, cpu.pc)
    }

    @Test fun conditionalJrTakenAndNotTaken() {
        assertEquals(12, run(0x20, 0x05)); assertEquals(0x107, cpu.pc)   // NZ with Z clear: taken
        cpu.pc = 0x100; cpu.f = Cpu.Z
        assertEquals(8, run(0x20, 0x05)); assertEquals(0x102, cpu.pc)    // not taken
    }

    @Test fun jrBackwards() { run(0x18, 0xFE); assertEquals(0x100, cpu.pc) }

    @Test fun conditionalRetCycles() { cpu.f = 0; assertEquals(8, run(0xC8)) } // RET Z not taken

    @Test fun rstJumpsToVector() { run(0xEF); assertEquals(0x28, cpu.pc) }

    @Test fun ldHlSpPlusOffsetFlagsFromLowByte() {
        cpu.sp = 0x00FF
        run(0xF8, 0x01)
        assertEquals(0x0100, cpu.hl); assertEquals(flags(false, false, true, true), cpu.f)
    }

    @Test fun addSpNegativeOffset() { cpu.sp = 0x0100; run(0xE8, 0xFF); assertEquals(0x00FF, cpu.sp) }

    @Test fun daaAfterBcdAddition() {
        cpu.a = 0x45; cpu.b = 0x38
        run(0x80, 0x27, steps = 2) // 45 + 38 = 7D -> DAA -> 83
        assertEquals(0x83, cpu.a)
    }

    @Test fun daaAfterBcdSubtraction() {
        cpu.a = 0x45; cpu.b = 0x38
        run(0x90, 0x27, steps = 2) // 45 - 38 = 0D -> DAA -> 07
        assertEquals(0x07, cpu.a)
    }

    @Test fun rotatesOnAClearZero() { cpu.a = 0x80; run(0x07); assertEquals(0x01, cpu.a); assertEquals(Cpu.C, cpu.f) }

    @Test fun cbSwap() { cpu.b = 0xA5; run(0xCB, 0x30); assertEquals(0x5A, cpu.b) }

    @Test fun cbBitTestsAndTimings() {
        cpu.b = 0x80
        assertEquals(8, run(0xCB, 0x78)) // BIT 7,B
        assertEquals(0, cpu.f and Cpu.Z)
        cpu.hl = 0xC000
        cpu.pc = 0x100
        assertEquals(12, run(0xCB, 0x46)) // BIT 0,(HL)
        assertTrue(cpu.f and Cpu.Z != 0)
    }

    @Test fun cbSetAndRes() {
        run(0xCB, 0xC7, 0xCB, 0x87, steps = 1); assertEquals(1, cpu.a)
        cpu.step(); assertEquals(0, cpu.a)
    }

    @Test fun cbSlaSraSrl() {
        cpu.b = 0x81; run(0xCB, 0x20); assertEquals(0x02, cpu.b); assertEquals(Cpu.C, cpu.f)
        cpu.pc = 0x100; cpu.b = 0x81; run(0xCB, 0x28); assertEquals(0xC0, cpu.b)
        cpu.pc = 0x100; cpu.b = 0x81; run(0xCB, 0x38); assertEquals(0x40, cpu.b)
    }

    @Test fun eiTakesEffectAfterNextInstruction() {
        run(0xFB, 0x00, steps = 1)
        assertFalse(cpu.ime)
        cpu.step()
        assertTrue(cpu.ime)
    }

    @Test fun interruptIsServicedAtVector() {
        cpu.ime = true; ints.enable = 1; ints.request(Interrupts.VBLANK)
        assertEquals(20, cpu.step())
        assertEquals(0x40, cpu.pc); assertFalse(cpu.ime)
        assertEquals(0, ints.flags and 1)
        assertEquals(0x100, bus.mem[0xFFFC] or (bus.mem[0xFFFD] shl 8))
    }

    @Test fun lowerBitInterruptHasPriority() {
        cpu.ime = true; ints.enable = 0x1F; ints.request(Interrupts.TIMER); ints.request(Interrupts.VBLANK)
        cpu.step()
        assertEquals(0x40, cpu.pc)
    }

    @Test fun haltWaitsUntilInterruptEvenWithImeOff() {
        run(0x76)
        assertTrue(cpu.halted)
        assertEquals(4, cpu.step()); assertTrue(cpu.halted)
        ints.enable = 1; ints.request(Interrupts.VBLANK)
        cpu.step()
        assertFalse(cpu.halted)
    }

    @Test fun retiEnablesInterruptsImmediately() {
        cpu.sp = 0xFFFC; bus.mem[0xFFFC] = 0x34; bus.mem[0xFFFD] = 0x12
        run(0xD9)
        assertEquals(0x1234, cpu.pc); assertTrue(cpu.ime)
    }
}
