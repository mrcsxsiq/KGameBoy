package dev.kgameboy.core.video

import dev.kgameboy.core.interrupt.Interrupts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PpuTest {
    private val ints = Interrupts().also { it.flags = 0 }
    private val ppu = Ppu(ints)

    private fun ticks(dots: Int) = ppu.tick(dots)

    /** Writes a tile whose every row is [lo]/[hi] bit planes at tile index [n] in unsigned data. */
    private fun tile(n: Int, lo: Int, hi: Int) {
        for (row in 0 until 8) { ppu.vram[n * 16 + row * 2] = lo.toByte(); ppu.vram[n * 16 + row * 2 + 1] = hi.toByte() }
    }

    private fun sprite(y: Int, x: Int, tile: Int, attr: Int) {
        ppu.oam[0] = y.toByte(); ppu.oam[1] = x.toByte(); ppu.oam[2] = tile.toByte(); ppu.oam[3] = attr.toByte()
    }

    private fun dark() = Ppu.COLORS[3]
    private fun light() = Ppu.COLORS[0]

    @Test fun lineTakes456Dots() { ticks(452); assertEquals(0, ppu.ly); ticks(4); assertEquals(1, ppu.ly) }

    @Test fun frameTakes70224Dots() {
        ticks(Ppu.FRAME_DOTS - 4); assertEquals(153, ppu.ly)
        ticks(4); assertEquals(0, ppu.ly)
    }

    @Test fun modesFollowTheScanlineTimeline() {
        assertEquals(2, ppu.readStat() and 3)
        ticks(80); assertEquals(3, ppu.readStat() and 3)
        ticks(172); assertEquals(0, ppu.readStat() and 3)
    }

    @Test fun vblankStartsAtLine144AndRequestsInterrupt() {
        ticks(456 * 143); assertEquals(0, ints.flags and 1)
        ticks(456)
        assertEquals(144, ppu.ly); assertEquals(1, ppu.readStat() and 3)
        assertEquals(1, ints.flags and 1); assertTrue(ppu.frameReady)
    }

    @Test fun lcdOffResetsLineAndStopsTime() {
        ticks(456 * 5)
        ppu.writeLcdc(0x11)
        ticks(1000)
        assertEquals(0, ppu.ly)
    }

    @Test fun lycCoincidenceRaisesStatWhenEnabled() {
        ppu.lyc = 2
        ppu.writeStat(0x40)
        ticks(456 * 2)
        assertTrue(ints.flags and 2 != 0)
        assertEquals(0x04, ppu.readStat() and 0x04)
    }

    @Test fun decodesTwoBitPerPixelBackgroundTile() {
        // BG on, unsigned tile data at 0x8000, map 0x9800. Tile 0: left half colour 3, right half 0.
        ppu.writeLcdc(0x91)
        ppu.bgp = 0xE4 // identity palette
        tile(0, lo = 0xF0, hi = 0xF0)
        ticks(456)
        assertEquals(dark(), ppu.framebuffer[0])
        assertEquals(dark(), ppu.framebuffer[3])
        assertEquals(light(), ppu.framebuffer[4])
    }

    @Test fun scrollShiftsBackground() {
        ppu.bgp = 0xE4
        tile(0, lo = 0xF0, hi = 0xF0)
        ppu.scx = 4
        ticks(456)
        assertEquals(light(), ppu.framebuffer[0])  // first pixel now comes from the tile's right half
        assertEquals(dark(), ppu.framebuffer[4])   // next tile's left half
    }

    @Test fun paletteRemapsShades() {
        tile(0, lo = 0xFF, hi = 0x00) // colour 1 everywhere
        ppu.bgp = 0b00_00_11_00       // colour 1 -> shade 3
        ticks(456)
        assertEquals(dark(), ppu.framebuffer[0])
    }

    @Test fun spriteIsDrawnAndFlipped() {
        ppu.writeLcdc(0x93) // BG + sprites
        ppu.obp0 = 0xE4
        tile(1, lo = 0xF0, hi = 0xF0) // left half opaque
        sprite(16, 8, 1, 0) // at screen (0,0)
        ticks(456)
        assertEquals(dark(), ppu.framebuffer[0])
        assertEquals(light(), ppu.framebuffer[7])

        sprite(16, 8, 1, 0x20) // X flip
        ticks(Ppu.FRAME_DOTS)
        assertEquals(light(), ppu.framebuffer[0])
        assertEquals(dark(), ppu.framebuffer[7])
    }

    @Test fun spriteBehindBackgroundOnlyShowsOverColourZero() {
        ppu.writeLcdc(0x93)
        ppu.bgp = 0xE4; ppu.obp0 = 0xE4
        tile(0, lo = 0xFF, hi = 0x00)     // background colour 1
        tile(1, lo = 0xFF, hi = 0xFF)     // sprite colour 3
        sprite(16, 8, 1, 0x80) // behind BG
        ticks(456)
        assertEquals(Ppu.COLORS[1], ppu.framebuffer[0]) // background wins
    }
}
