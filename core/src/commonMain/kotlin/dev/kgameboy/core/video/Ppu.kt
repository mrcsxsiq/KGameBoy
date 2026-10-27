package dev.kgameboy.core.video

import dev.kgameboy.core.interrupt.Interrupts

/**
 * Pixel Processing Unit. Each of the 154 scanlines lasts 456 dots (T-cycles):
 *
 *   lines 0-143: mode 2 OAM scan (80) -> mode 3 drawing (172) -> mode 0 HBlank (204)
 *   lines 144-153: mode 1 VBlank
 *
 * A line is rendered in one go when mode 3 ends. The result goes into [framebuffer] as ARGB.
 */
class Ppu(private val interrupts: Interrupts) {
    val vram = ByteArray(0x2000)
    val oam = ByteArray(0xA0)
    val framebuffer = IntArray(WIDTH * HEIGHT)

    var lcdc = 0x91
    private var statEnables = 0
    var scy = 0; var scx = 0
    var ly = 0; var lyc = 0
    var bgp = 0xFC; var obp0 = 0xFF; var obp1 = 0xFF
    var wy = 0; var wx = 0

    private var mode = 2
    private var dot = 0
    private var windowLine = 0
    private var statLine = false
    private val bgIndex = IntArray(WIDTH) // BG/window colour index per pixel, for sprite priority

    /** True when a new frame has just been completed (set at VBlank, cleared by the caller). */
    var frameReady = false

    val lcdOn: Boolean get() = lcdc and 0x80 != 0

    fun readStat(): Int {
        val coincidence = if (ly == lyc) 0x04 else 0
        return 0x80 or statEnables or coincidence or (if (lcdOn) mode else 0)
    }

    fun writeStat(value: Int) { statEnables = value and 0x78; updateStatLine() }

    fun writeLcdc(value: Int) {
        val wasOn = lcdOn
        lcdc = value
        if (wasOn && !lcdOn) { ly = 0; dot = 0; mode = 0; windowLine = 0; statLine = false }
        if (!wasOn && lcdOn) { mode = 2; dot = 0; updateStatLine() }
    }

    fun tick(cycles: Int) {
        if (!lcdOn) return
        var left = cycles
        while (left > 0) {
            left -= 4
            dot += 4
            if (dot >= LINE_DOTS) {
                dot -= LINE_DOTS
                ly++
                if (ly == HEIGHT) {
                    interrupts.request(Interrupts.VBLANK)
                    frameReady = true
                } else if (ly > 153) {
                    ly = 0
                    windowLine = 0
                }
            }
            val newMode = when {
                ly >= HEIGHT -> 1
                dot < 80 -> 2
                dot < 252 -> 3
                else -> 0
            }
            if (newMode != mode) {
                if (mode == 3 && newMode == 0) renderLine()
                mode = newMode
            }
            updateStatLine()
        }
    }

    private fun updateStatLine() {
        val line = (ly == lyc && statEnables and 0x40 != 0) ||
            (mode == 0 && statEnables and 0x08 != 0) ||
            (mode == 1 && statEnables and 0x10 != 0) ||
            (mode == 2 && statEnables and 0x20 != 0)
        if (line && !statLine) interrupts.request(Interrupts.STAT)
        statLine = line
    }

    // ---- rendering ----
    private fun vramAt(addr: Int) = vram[addr and 0x1FFF].toInt() and 0xFF

    private fun shade(palette: Int, index: Int) = COLORS[(palette shr (index * 2)) and 3]

    /** Colour index (0-3) of pixel [px] (0-7) in the 8-pixel-wide row [row] of tile at [tileAddr]. */
    private fun tilePixel(tileAddr: Int, row: Int, px: Int): Int {
        val lo = vramAt(tileAddr + row * 2)
        val hi = vramAt(tileAddr + row * 2 + 1)
        val bit = 7 - px
        return (((hi shr bit) and 1) shl 1) or ((lo shr bit) and 1)
    }

    private fun renderLine() {
        val base = ly * WIDTH
        val bgEnabled = lcdc and 0x01 != 0
        val windowVisible = bgEnabled && lcdc and 0x20 != 0 && ly >= wy && wx <= 166
        var usedWindow = false
        val unsignedTiles = lcdc and 0x10 != 0

        for (x in 0 until WIDTH) {
            var index = 0
            if (bgEnabled) {
                val inWindow = windowVisible && x >= wx - 7
                val mapBase: Int; val mx: Int; val my: Int
                if (inWindow) {
                    usedWindow = true
                    mapBase = if (lcdc and 0x40 != 0) 0x1C00 else 0x1800
                    mx = x - (wx - 7); my = windowLine
                } else {
                    mapBase = if (lcdc and 0x08 != 0) 0x1C00 else 0x1800
                    mx = (x + scx) and 0xFF; my = (ly + scy) and 0xFF
                }
                val tile = vramAt(mapBase + (my shr 3) * 32 + (mx shr 3))
                val tileAddr = if (unsignedTiles) tile * 16 else 0x1000 + tile.toByte().toInt() * 16
                index = tilePixel(tileAddr, my and 7, mx and 7)
            }
            bgIndex[x] = index
            framebuffer[base + x] = shade(bgp, index)
        }
        if (usedWindow) windowLine++
        if (lcdc and 0x02 != 0) renderSprites(base)
    }

    private fun renderSprites(base: Int) {
        val height = if (lcdc and 0x04 != 0) 16 else 8
        // Hardware draws at most 10 sprites per line, chosen in OAM order.
        val visible = ArrayList<Int>(10)
        for (i in 0 until 40) {
            val y = (oam[i * 4].toInt() and 0xFF) - 16
            if (ly >= y && ly < y + height) {
                visible.add(i)
                if (visible.size == 10) break
            }
        }
        // On DMG the sprite with the lower X wins; ties go to the lower OAM index.
        visible.sortWith(compareBy({ oam[it * 4 + 1].toInt() and 0xFF }, { it }))

        val written = BooleanArray(WIDTH)
        for (i in visible) {
            val y = (oam[i * 4].toInt() and 0xFF) - 16
            val sx = (oam[i * 4 + 1].toInt() and 0xFF) - 8
            var tile = oam[i * 4 + 2].toInt() and 0xFF
            val attr = oam[i * 4 + 3].toInt() and 0xFF
            var row = ly - y
            if (attr and 0x40 != 0) row = height - 1 - row
            if (height == 16) tile = tile and 0xFE
            val tileAddr = tile * 16
            val palette = if (attr and 0x10 != 0) obp1 else obp0
            for (px in 0 until 8) {
                val x = sx + px
                if (x < 0 || x >= WIDTH || written[x]) continue
                val col = if (attr and 0x20 != 0) 7 - px else px
                val index = tilePixel(tileAddr, row, col)
                if (index == 0) continue
                written[x] = true
                if (attr and 0x80 != 0 && bgIndex[x] != 0) continue
                framebuffer[base + x] = shade(palette, index)
            }
        }
    }

    companion object {
        const val WIDTH = 160
        const val HEIGHT = 144
        const val LINE_DOTS = 456
        const val FRAME_DOTS = LINE_DOTS * 154 // 70224

        /** Classic DMG green shades, lightest to darkest. */
        val COLORS = intArrayOf(0xFFE0F8D0.toInt(), 0xFF88C070.toInt(), 0xFF346856.toInt(), 0xFF081820.toInt())
    }
}
