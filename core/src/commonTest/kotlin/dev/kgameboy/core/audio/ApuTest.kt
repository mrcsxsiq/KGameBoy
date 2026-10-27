package dev.kgameboy.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApuTest {
    private val apu = Apu()

    private fun collect(cycles: Int): List<Short> {
        val out = ArrayList<Short>()
        apu.tick(cycles)
        apu.drain { s, n -> for (i in 0 until n) out.add(s[i]) }
        return out
    }

    /** Programs channel 2 as a loud, steady square wave. */
    private fun startSquare(freq: Int = 1792, nr21: Int = 0x80, length: Boolean = false) {
        apu.write(0xFF17, 0xF0)                       // volume 15, no envelope change
        apu.write(0xFF16, nr21)                       // 50% duty
        apu.write(0xFF18, freq and 0xFF)
        apu.write(0xFF19, 0x80 or (if (length) 0x40 else 0) or (freq shr 8))
    }

    @Test fun producesOneStereoSamplePerCycleBudget() {
        val samples = collect(70224)
        // 70224 cycles at 44100 Hz is about 738 sample frames, two shorts each.
        assertTrue(samples.size in 1470..1480, "got ${samples.size}")
    }

    @Test fun silentWhenNoChannelTriggered() {
        assertTrue(collect(70224).all { it.toInt() in -2..2 })
    }

    @Test fun triggeredPulseChannelIsAudibleAndEnabled() {
        startSquare()
        assertEquals(0x02, apu.read(0xFF26) and 0x02)
        val samples = collect(70224 * 2)
        assertTrue(samples.any { it > 1000 } && samples.any { it < -1000 }, "square wave should swing both ways")
    }

    @Test fun lengthCounterSilencesChannel() {
        startSquare(nr21 = 0x80 or 0x3F, length = true) // length 64 - 63 = 1 tick of 256 Hz
        collect(8192 * 2) // frame sequencer clocks length on step 0 and 2
        assertEquals(0, apu.read(0xFF26) and 0x02)
    }

    @Test fun dacOffDisablesChannel() {
        startSquare()
        apu.write(0xFF17, 0x00)
        assertEquals(0, apu.read(0xFF26) and 0x02)
    }

    @Test fun registerReadsOrInUnusedBits() {
        apu.write(0xFF12, 0x00)
        assertEquals(0xFF, apu.read(0xFF13))            // write-only frequency low byte
        assertEquals(0x3F, apu.read(0xFF11) and 0x3F)   // low bits of NR11 read as 1
    }

    @Test fun powerOffClearsRegistersAndBlocksWrites() {
        apu.write(0xFF12, 0xF3)
        apu.write(0xFF26, 0x00)
        assertEquals(0x70, apu.read(0xFF26) and 0xF0)
        apu.write(0xFF12, 0xF3)
        assertEquals(0x00, apu.read(0xFF12))
    }

    @Test fun waveRamIsDirectlyAddressable() {
        apu.write(0xFF30, 0xAB)
        assertEquals(0xAB, apu.read(0xFF30))
    }

    @Test fun waveChannelPlaysWaveRam() {
        for (i in 0 until 16) apu.write(0xFF30 + i, if (i < 8) 0xFF else 0x00)
        apu.write(0xFF1A, 0x80)           // DAC on
        apu.write(0xFF1C, 0x20)           // 100% volume
        apu.write(0xFF1D, 0x00); apu.write(0xFF1E, 0x87) // trigger, high frequency
        assertEquals(0x04, apu.read(0xFF26) and 0x04)
        val samples = collect(70224)
        assertTrue(samples.any { it > 500 })
    }

    @Test fun noiseChannelProducesVaryingOutput() {
        apu.write(0xFF21, 0xF0); apu.write(0xFF22, 0x11); apu.write(0xFF23, 0x80)
        assertEquals(0x08, apu.read(0xFF26) and 0x08)
        assertTrue(collect(70224).toSet().size > 3)
    }

    @Test fun panningSilencesChannelOnDisabledSide() {
        startSquare()
        apu.write(0xFF25, 0x02) // channel 2 to the right only
        val samples = collect(70224)
        val left = samples.filterIndexed { i, _ -> i % 2 == 0 }
        val right = samples.filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(left.all { it.toInt() in -2..2 })
        assertTrue(right.any { it > 1000 })
    }
}
