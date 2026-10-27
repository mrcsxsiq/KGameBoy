package dev.kgameboy.core

import dev.kgameboy.core.cartridge.Cartridge
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs real test ROMs headless and reads their verdict from the serial port, which is how
 * Blargg's ROMs report results. ROMs that are not on disk are skipped; see roms/README.md.
 */
class RomIntegrationTest {
    private fun romFile(name: String): File? =
        listOf("../roms/third-party/$name", "../roms/$name").map(::File).firstOrNull { it.exists() }

    private fun runSerial(rom: File, maxCycles: Long): String {
        val gb = GameBoy(Cartridge.load(rom.readBytes()), video = {}, audio = { _, _ -> })
        val out = StringBuilder()
        gb.serial.onByte = { out.append(it.toChar()) }
        while (gb.totalCycles < maxCycles) {
            gb.step()
            if (out.contains("Passed") || out.contains("Failed")) break
        }
        return out.toString()
    }

    @Test
    fun blarggCpuInstrs() {
        val rom = romFile("cpu_instrs.gb") ?: return
        val out = runSerial(rom, 600_000_000)
        assertTrue(out.contains("Passed"), "cpu_instrs output was:\n$out")
    }

    @Test
    fun blarggInstrTiming() {
        val rom = romFile("instr_timing.gb") ?: return
        val out = runSerial(rom, 100_000_000)
        assertTrue(out.contains("Passed"), "instr_timing output was:\n$out")
    }
}
