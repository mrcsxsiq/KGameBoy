package dev.kgameboy.core.video

import dev.kgameboy.core.interrupt.Interrupts

/**
 * Pixel Processing Unit. Each of the 154 scanlines lasts 456 dots (T-cycles):
 *
 *   lines 0-143: mode 2 OAM scan (80) -> mode 3 drawing (172) -> mode 0 HBlank (204)
 *   lines 144-153: mode 1 VBlank
 *
 * A line is rendered in one go when mode 3 ends. The result goes into [framebuffer] as ARGB.
 *
 * With [cgb] set the PPU behaves like a Game Boy Color: two VRAM banks, per-tile attributes, and
 * 8 background plus 8 sprite palettes of four RGB555 colours each.
 */
class Ppu(private val interrupts: Interrupts, val cgb: Boolean = false) {
    /** Two 8 KiB banks. DMG only ever uses the first one. */
    val vram = ByteArray(0x4000)
    val oam = ByteArray(0xA0)
    val framebuffer = IntArray(WIDTH * HEIGHT)

    /** Selected VRAM bank for CPU access (VBK, 0xFF4F). */
    var vramBank = 0

    /** Colour RAM: 8 palettes x 4 colours x 2 bytes, for background and for sprites. */
    val bgPalette = ByteArray(64)
    val objPalette = ByteArray(64)

    /** BCPS/OCPS: bit 7 = auto-increment after a write, bits 0-5 = index into colour RAM. */
    var bcps = 0
    var ocps = 0

    /** Called when a visible line enters HBlank; the CGB HBlank DMA hooks in here. */
    var onHBlank: (() -> Unit)? = null

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
    private val bgIndex = IntArray(WIDTH)      // BG/window colour index per pixel, for sprite priority
    private val bgPriority = BooleanArray(WIDTH) // CGB: tile attribute "BG over sprites" per pixel

    /** True when a new frame has just been completed (set at VBlank, cleared by the caller). */
    var frameReady = false

    val lcdOn: Boolean get() = lcdc and 0x80 != 0

    // ---- CGB colour RAM access (BCPD/OCPD) ----
    fun readBgPalette(): Int = bgPalette[bcps and 0x3F].toInt() and 0xFF
    fun readObjPalette(): Int = objPalette[ocps and 0x3F].toInt() and 0xFF

    fun writeBgPalette(value: Int) {
        bgPalette[bcps and 0x3F] = value.toByte()
        if (bcps and 0x80 != 0) bcps = 0x80 or ((bcps + 1) and 0x3F)
    }

    fun writeObjPalette(value: Int) {
        objPalette[ocps and 0x3F] = value.toByte()
        if (ocps and 0x80 != 0) ocps = 0x80 or ((ocps + 1) and 0x3F)
    }

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
                if (mode == 3 && newMode == 0) { renderLine(); onHBlank?.invoke() }
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
    private fun vramAt(bank: Int, addr: Int) = vram[bank * 0x2000 + (addr and 0x1FFF)].toInt() and 0xFF

    private fun shade(palette: Int, index: Int) = COLORS[(palette shr (index * 2)) and 3]

    private fun expand5(c: Int) = (c shl 3) or (c shr 2)

    /** Looks up colour [index] of CGB palette [palette] in [ram] and converts RGB555 to ARGB. */
    private fun cgbColor(ram: ByteArray, palette: Int, index: Int): Int {
        val i = palette * 8 + index * 2
        val rgb = (ram[i].toInt() and 0xFF) or ((ram[i + 1].toInt() and 0xFF) shl 8)
        val r = expand5(rgb and 0x1F)
        val g = expand5((rgb shr 5) and 0x1F)
        val b = expand5((rgb shr 10) and 0x1F)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Colour index (0-3) of pixel [px] (0-7) in row [row] of the tile at [tileAddr] in [bank]. */
    private fun tilePixel(bank: Int, tileAddr: Int, row: Int, px: Int): Int {
        val lo = vramAt(bank, tileAddr + row * 2)
        val hi = vramAt(bank, tileAddr + row * 2 + 1)
        val bit = 7 - px
        return (((hi shr bit) and 1) shl 1) or ((lo shr bit) and 1)
    }

    private fun renderLine() {
        val base = ly * WIDTH
        // On CGB, LCDC bit 0 no longer switches the background off; it controls sprite priority.
        val bgEnabled = cgb || lcdc and 0x01 != 0
        val windowVisible = bgEnabled && lcdc and 0x20 != 0 && ly >= wy && wx <= 166
        var usedWindow = false
        val unsignedTiles = lcdc and 0x10 != 0

        for (x in 0 until WIDTH) {
            var index = 0
            var color = shade(bgp, 0)
            var priority = false
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
                val mapAddr = mapBase + (my shr 3) * 32 + (mx shr 3)
                val tile = vramAt(0, mapAddr)
                val tileAddr = if (unsignedTiles) tile * 16 else 0x1000 + tile.toByte().toInt() * 16
                var row = my and 7
                var col = mx and 7
                var bank = 0
                var palette = 0
                if (cgb) {
                    // The tile attributes live in the second VRAM bank, at the same map address.
                    val attr = vramAt(1, mapAddr)
                    palette = attr and 7
                    bank = (attr shr 3) and 1
                    if (attr and 0x20 != 0) col = 7 - col
                    if (attr and 0x40 != 0) row = 7 - row
                    priority = attr and 0x80 != 0
                }
                index = tilePixel(bank, tileAddr, row, col)
                color = if (cgb) cgbColor(bgPalette, palette, index) else shade(bgp, index)
            }
            bgIndex[x] = index
            bgPriority[x] = priority
            framebuffer[base + x] = color
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
        // DMG: the sprite with the lower X wins, ties go to the lower OAM index.
        // CGB: only the OAM index matters, so the list is already in priority order.
        if (!cgb) visible.sortWith(compareBy({ oam[it * 4 + 1].toInt() and 0xFF }, { it }))
        val masterPriority = lcdc and 0x01 != 0

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
            val bank = if (cgb) (attr shr 3) and 1 else 0
            val dmgPalette = if (attr and 0x10 != 0) obp1 else obp0
            for (px in 0 until 8) {
                val x = sx + px
                if (x < 0 || x >= WIDTH || written[x]) continue
                val col = if (attr and 0x20 != 0) 7 - px else px
                val index = tilePixel(bank, tileAddr, row, col)
                if (index == 0) continue
                written[x] = true
                val behindBackground = if (cgb) {
                    masterPriority && (attr and 0x80 != 0 || bgPriority[x]) && bgIndex[x] != 0
                } else {
                    attr and 0x80 != 0 && bgIndex[x] != 0
                }
                if (behindBackground) continue
                framebuffer[base + x] =
                    if (cgb) cgbColor(objPalette, attr and 7, index) else shade(dmgPalette, index)
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
