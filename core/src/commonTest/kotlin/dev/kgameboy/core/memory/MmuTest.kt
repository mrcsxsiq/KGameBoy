package dev.kgameboy.core.memory

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import kotlin.test.Test
import kotlin.test.assertEquals

class MmuTest {
    private val rom = ByteArray(0x8000).also {
        "TEST".forEachIndexed { i, c -> it[0x134 + i] = c.code.toByte() }
        it[0x150] = 0x77
        var x = 0
        for (i in 0x134..0x14C) x = (x - (it[i].toInt() and 0xFF) - 1) and 0xFF
        it[0x14D] = x.toByte()
    }
    private val gb = GameBoy(Cartridge.load(rom), video = {}, audio = { _, _ -> })
    private val mmu = gb.mmu

    @Test fun readsRomThroughCartridge() { assertEquals(0x77, mmu.read(0x150)) }

    @Test fun workRamIsReadWrite() { mmu.write(0xC123, 0xAB); assertEquals(0xAB, mmu.read(0xC123)) }

    @Test fun echoRamMirrorsWorkRam() {
        mmu.write(0xC010, 0x5A)
        assertEquals(0x5A, mmu.read(0xE010))
        mmu.write(0xE020, 0x3C)
        assertEquals(0x3C, mmu.read(0xC020))
    }

    @Test fun highRamAndInterruptEnable() {
        mmu.write(0xFF90, 0x11); assertEquals(0x11, mmu.read(0xFF90))
        mmu.write(0xFFFF, 0x1F); assertEquals(0x1F, mmu.read(0xFFFF))
    }

    @Test fun vramAndOamAreRoutedToPpu() {
        mmu.write(0x8000, 0x42); assertEquals(0x42, gb.ppu.vram[0].toInt())
        mmu.write(0xFE00, 0x24); assertEquals(0x24, gb.ppu.oam[0].toInt())
    }

    @Test fun unusableRegionReadsFf() { assertEquals(0xFF, mmu.read(0xFEA5)) }

    @Test fun divWriteResetsTimer() {
        gb.timer.tick(1024)
        mmu.write(0xFF04, 0x55)
        assertEquals(0, mmu.read(0xFF04))
    }

    @Test fun ioRegistersReachComponents() {
        mmu.write(0xFF47, 0x1B); assertEquals(0x1B, gb.ppu.bgp)
        mmu.write(0xFF06, 0x33); assertEquals(0x33, gb.timer.tma)
        mmu.write(0xFF0F, 0x01); assertEquals(0xE1, mmu.read(0xFF0F))
    }

    @Test fun lyIsReadOnly() { mmu.write(0xFF44, 0x50); assertEquals(0, mmu.read(0xFF44)) }

    @Test fun oamDmaCopiesFromSourcePage() {
        for (i in 0 until 0xA0) mmu.write(0xC000 + i, i)
        mmu.write(0xFF46, 0xC0)
        assertEquals(0x00, gb.ppu.oam[0].toInt() and 0xFF)
        assertEquals(0x9F, gb.ppu.oam[0x9F].toInt() and 0xFF)
    }
}
