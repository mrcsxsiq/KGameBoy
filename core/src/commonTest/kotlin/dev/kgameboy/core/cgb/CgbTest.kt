package dev.kgameboy.core.cgb

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.cartridge.CartridgeHeader
import dev.kgameboy.core.interrupt.Interrupts
import dev.kgameboy.core.video.Ppu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CgbTest {
    /** A 32 KiB ROM with a valid header checksum. [cgbFlag] goes to 0x0143; [code] starts at 0x0100. */
    private fun rom(cgbFlag: Int, title: String = "CGBTEST", vararg code: Int): ByteArray {
        val rom = ByteArray(0x8000)
        title.forEachIndexed { i, c -> rom[0x134 + i] = c.code.toByte() }
        rom[0x143] = cgbFlag.toByte()
        code.forEachIndexed { i, b -> rom[0x100 + i] = b.toByte() }
        var x = 0
        for (i in 0x134..0x14C) x = (x - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        rom[0x14D] = x.toByte()
        return rom
    }

    private fun machine(cgbFlag: Int, vararg code: Int) =
        GameBoy(Cartridge.load(rom(cgbFlag, "CGBTEST", *code)), video = {}, audio = { _, _ -> })

    // ---- header ----

    @Test fun headerDetectsCgbFlagAndKeepsTitleClean() {
        val h = CartridgeHeader.parse(rom(0x80))
        assertTrue(h.cgbSupported)
        assertEquals("CGBTEST", h.title)
        assertTrue(CartridgeHeader.parse(rom(0xC0)).cgbSupported)
        assertFalse(CartridgeHeader.parse(rom(0x00)).cgbSupported)
    }

    @Test fun machineRunsInCgbModeOnlyForCgbCartridges() {
        assertTrue(machine(0x80).cgb)
        assertFalse(machine(0x00).cgb)
        assertEquals(0x11, machine(0x80).cpu.a) // CGB boot ROM leaves A = 0x11
        assertEquals(0x01, machine(0x00).cpu.a)
    }

    // ---- banked memory ----

    @Test fun vramHasTwoBanksSelectedByVbk() {
        val mmu = machine(0x80).mmu
        mmu.write(0xFF4F, 1); mmu.write(0x8000, 0x42)
        mmu.write(0xFF4F, 0)
        assertEquals(0, mmu.read(0x8000))
        mmu.write(0xFF4F, 1)
        assertEquals(0x42, mmu.read(0x8000))
        assertEquals(0xFF, mmu.read(0xFF4F))
    }

    @Test fun workRamBanksOneToSevenShareTheD000Window() {
        val mmu = machine(0x80).mmu
        mmu.write(0xC000, 0x99) // bank 0 is fixed
        mmu.write(0xFF70, 2); mmu.write(0xD000, 0x11)
        mmu.write(0xFF70, 3); mmu.write(0xD000, 0x22)
        mmu.write(0xFF70, 2); assertEquals(0x11, mmu.read(0xD000))
        mmu.write(0xFF70, 3); assertEquals(0x22, mmu.read(0xD000))
        assertEquals(0x99, mmu.read(0xC000))
        mmu.write(0xFF70, 0) // bank 0 selects bank 1
        assertEquals(0xF9, mmu.read(0xFF70))
    }

    @Test fun dmgModeIgnoresCgbRegisters() {
        val gb = machine(0x00)
        gb.mmu.write(0xFF4F, 1)
        assertEquals(0, gb.ppu.vramBank)
        assertEquals(0xFF, gb.mmu.read(0xFF4F))
    }

    // ---- colour RAM ----

    @Test fun paletteIndexAutoIncrements() {
        val gb = machine(0x80)
        gb.mmu.write(0xFF68, 0x80)
        gb.mmu.write(0xFF69, 0x1F); gb.mmu.write(0xFF69, 0x7C)
        assertEquals(0x1F, gb.ppu.bgPalette[0].toInt()); assertEquals(0x7C, gb.ppu.bgPalette[1].toInt())
        assertEquals(0x82, gb.mmu.read(0xFF68) and 0xBF)
        gb.mmu.write(0xFF68, 0x00) // no auto-increment
        gb.mmu.write(0xFF69, 0x55); gb.mmu.write(0xFF69, 0x66)
        assertEquals(0x66, gb.ppu.bgPalette[0].toInt())
    }

    // ---- PPU in colour mode ----

    private val ints = Interrupts().also { it.flags = 0 }
    private val ppu = Ppu(ints, cgb = true)

    private fun bgColor(palette: Int, index: Int, rgb555: Int) {
        ppu.bgPalette[palette * 8 + index * 2] = rgb555.toByte()
        ppu.bgPalette[palette * 8 + index * 2 + 1] = (rgb555 shr 8).toByte()
    }

    private fun tile(bank: Int, n: Int, lo: Int, hi: Int) {
        for (row in 0 until 8) {
            ppu.vram[bank * 0x2000 + n * 16 + row * 2] = lo.toByte()
            ppu.vram[bank * 0x2000 + n * 16 + row * 2 + 1] = hi.toByte()
        }
    }

    private val red = 0xFFFF0000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun backgroundUsesRgb555PaletteFromAttributes() {
        bgColor(0, 1, 0x001F)  // pure red
        tile(0, 0, lo = 0xFF, hi = 0x00)
        ppu.tick(456)
        assertEquals(red, ppu.framebuffer[0])
    }

    @Test fun tileAttributeSelectsPalette() {
        bgColor(3, 1, 0x7FFF)  // white, in palette 3
        tile(0, 0, lo = 0xFF, hi = 0x00)
        ppu.vram[0x2000 + 0x1800] = 3 // attribute of map entry (0,0): palette 3
        ppu.tick(456)
        assertEquals(white, ppu.framebuffer[0])
        assertEquals(black, ppu.framebuffer[8]) // next tile uses palette 0, colour 1 = black (unset)
    }

    @Test fun tileAttributeFlipsHorizontally() {
        bgColor(0, 3, 0x7FFF)
        tile(0, 1, lo = 0xF0, hi = 0xF0)       // left half colour 3, right half colour 0
        ppu.vram[0x1800] = 1
        ppu.tick(456)
        assertEquals(white, ppu.framebuffer[0])
        ppu.tick(70224)                         // next frame, now flipped
        ppu.vram[0x2000 + 0x1800] = 0x20
        ppu.tick(70224)
        assertEquals(black, ppu.framebuffer[0])
        assertEquals(white, ppu.framebuffer[7])
    }

    @Test fun tileAttributeReadsTileDataFromSecondBank() {
        bgColor(0, 3, 0x7FFF)
        tile(1, 0, lo = 0xFF, hi = 0xFF)       // only bank 1 holds the pixels
        ppu.vram[0x2000 + 0x1800] = 0x08
        ppu.tick(456)
        assertEquals(white, ppu.framebuffer[0])
    }

    @Test fun spriteUsesObjectPaletteFromAttribute() {
        ppu.writeLcdc(0x93)
        ppu.objPalette[2 * 8 + 2] = 0xE0.toByte(); ppu.objPalette[2 * 8 + 3] = 0x03 // palette 2 colour 1 = green
        tile(0, 1, lo = 0xFF, hi = 0x00)
        ppu.oam[0] = 16; ppu.oam[1] = 8; ppu.oam[2] = 1; ppu.oam[3] = 2
        ppu.tick(456)
        assertEquals(0xFF00FF00.toInt(), ppu.framebuffer[0])
    }

    @Test fun lowerOamIndexWinsRegardlessOfX() {
        ppu.writeLcdc(0x93)
        ppu.objPalette[0 * 8 + 2] = 0x1F; ppu.objPalette[0 * 8 + 3] = 0x00 // palette 0 colour 1 = red
        ppu.objPalette[1 * 8 + 2] = 0xE0.toByte(); ppu.objPalette[1 * 8 + 3] = 0x03 // palette 1 colour 1 = green
        tile(0, 1, lo = 0xFF, hi = 0x00)
        // Sprite 0 (red) sits to the right of sprite 1 (green); they overlap on pixels 4..7.
        ppu.oam[0] = 16; ppu.oam[1] = 12; ppu.oam[2] = 1; ppu.oam[3] = 0
        ppu.oam[4] = 16; ppu.oam[5] = 8; ppu.oam[6] = 1; ppu.oam[7] = 1
        ppu.tick(456)
        assertEquals(0xFF00FF00.toInt(), ppu.framebuffer[0]) // only sprite 1 covers pixel 0
        assertEquals(red, ppu.framebuffer[5])                 // overlap: sprite 0 wins on CGB
    }

    @Test fun masterPriorityOffPutsSpritesAboveEverything() {
        bgColor(0, 1, 0x7FFF)
        ppu.objPalette[2] = 0x1F; ppu.objPalette[3] = 0x00
        tile(0, 0, lo = 0xFF, hi = 0x00)       // background colour 1
        tile(0, 1, lo = 0xFF, hi = 0x00)       // sprite colour 1
        ppu.oam[0] = 16; ppu.oam[1] = 8; ppu.oam[2] = 1; ppu.oam[3] = 0x80.toByte() // "behind background"
        ppu.writeLcdc(0x93)                    // LCDC bit 0 on: background wins
        ppu.tick(456)
        assertEquals(white, ppu.framebuffer[0])
        ppu.writeLcdc(0x92)                    // bit 0 off: sprite always on top
        ppu.tick(70224)
        assertEquals(red, ppu.framebuffer[0])
    }

    // ---- HDMA ----

    private fun setHdma(mmu: dev.kgameboy.core.memory.Mmu, src: Int, dest: Int) {
        mmu.write(0xFF51, src shr 8); mmu.write(0xFF52, src and 0xFF)
        mmu.write(0xFF53, dest shr 8); mmu.write(0xFF54, dest and 0xFF)
    }

    @Test fun generalDmaCopiesAllBlocksImmediately() {
        val gb = machine(0x80)
        for (i in 0 until 0x20) gb.mmu.write(0xC000 + i, i + 1)
        setHdma(gb.mmu, 0xC000, 0x0000)
        gb.mmu.write(0xFF55, 0x01) // 2 blocks, general purpose
        for (i in 0 until 0x20) assertEquals(i + 1, gb.ppu.vram[i].toInt())
        assertEquals(0xFF, gb.mmu.read(0xFF55))
    }

    @Test fun hblankDmaCopiesOneBlockPerHblank() {
        val gb = machine(0x80)
        for (i in 0 until 0x20) gb.mmu.write(0xC000 + i, 0x80 + i)
        setHdma(gb.mmu, 0xC000, 0x0000)
        gb.mmu.write(0xFF55, 0x81) // 2 blocks, HBlank mode
        assertEquals(0x01, gb.mmu.read(0xFF55)) // blocks left - 1
        assertEquals(0, gb.ppu.vram[0].toInt())
        gb.ppu.tick(252)            // first line reaches HBlank
        assertEquals(0x80, gb.ppu.vram[0].toInt() and 0xFF)
        assertEquals(0, gb.ppu.vram[0x10].toInt())
        gb.ppu.tick(456)            // second line
        assertEquals(0x90, gb.ppu.vram[0x10].toInt() and 0xFF)
        assertEquals(0xFF, gb.mmu.read(0xFF55)) // finished
    }

    @Test fun clearingBit7CancelsHblankDma() {
        val gb = machine(0x80)
        setHdma(gb.mmu, 0xC000, 0x0000)
        gb.mmu.write(0xFF55, 0x83)
        gb.mmu.write(0xFF55, 0x03)
        assertEquals(0xFF, gb.mmu.read(0xFF55))
    }

    // ---- double speed ----

    @Test fun stopSwitchesToDoubleSpeedOnlyWhenArmed() {
        val gb = machine(0x80, 0x10, 0x00, 0x10, 0x00) // STOP, STOP
        gb.step()
        assertEquals(0x7E, gb.mmu.read(0xFF4D)) // not armed: still normal speed
        gb.mmu.write(0xFF4D, 1)
        gb.step()
        assertEquals(0xFE, gb.mmu.read(0xFF4D)) // double speed, nothing armed
    }

    @Test fun doubleSpeedRunsTwiceAsManyCpuCyclesPerFrame() {
        val gb = machine(0x80, 0x10, 0x00)
        gb.mmu.write(0xFF4D, 1)
        gb.step() // switch
        val before = gb.totalCycles
        gb.runFrame()
        val used = gb.totalCycles - before
        assertTrue(used in 140448L - 8..140448L + 32, "cycles per frame in double speed: $used")
    }
}
