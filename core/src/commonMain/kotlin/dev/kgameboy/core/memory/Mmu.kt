package dev.kgameboy.core.memory

import dev.kgameboy.core.audio.Apu
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.input.Joypad
import dev.kgameboy.core.interrupt.Interrupts
import dev.kgameboy.core.serial.Serial
import dev.kgameboy.core.timer.Timer
import dev.kgameboy.core.video.Ppu

/**
 * The address bus. Decodes the 16-bit address space and forwards each access to the component
 * that owns that region:
 *
 *   0000-7FFF cartridge ROM      8000-9FFF VRAM (PPU)        A000-BFFF cartridge RAM
 *   C000-DFFF work RAM           E000-FDFF echo of work RAM  FE00-FE9F OAM (PPU)
 *   FEA0-FEFF unusable           FF00-FF7F I/O registers     FF80-FFFE high RAM
 *   FFFF interrupt enable
 */
class Mmu(
    private val cartridge: Cartridge,
    val interrupts: Interrupts,
    val timer: Timer,
    val joypad: Joypad,
    val serial: Serial,
    val ppu: Ppu,
    val apu: Apu,
    /** Game Boy Color mode: banked VRAM/WRAM, colour palettes, HDMA and double speed. */
    val cgb: Boolean = false,
) : Bus {
    // 8 banks of 4 KiB. DMG only uses banks 0 and 1, which together form its 8 KiB of work RAM.
    private val wram = ByteArray(0x8000)
    private var wramBank = 1
    private val hram = ByteArray(0x80)
    private val io = IntArray(0x80) // registers without a dedicated component (e.g. FF50)

    /** True while the CPU runs at double speed (CGB only). */
    var doubleSpeed = false
        private set
    private var speedPrepare = false

    // HDMA (CGB): source and destination of the next 16-byte block, blocks left, HBlank mode flag.
    private var hdmaSource = 0
    private var hdmaDest = 0
    private var hdmaBlocks = 0
    private var hdmaActive = false

    init {
        if (cgb) ppu.onHBlank = { if (hdmaActive) hdmaBlock() }
    }

    /** Called by STOP: switches speed if the program armed it through KEY1 (0xFF4D). */
    fun trySpeedSwitch() {
        if (cgb && speedPrepare) { doubleSpeed = !doubleSpeed; speedPrepare = false }
    }

    private fun wramIndex(addr: Int): Int {
        val offset = addr and 0x1FFF // also folds the E000-FDFF echo onto C000-DDFF
        return if (offset < 0x1000) offset else wramBank * 0x1000 + (offset - 0x1000)
    }

    private fun hdmaBlock() {
        for (i in 0 until 0x10) {
            ppu.vram[ppu.vramBank * 0x2000 + ((hdmaDest + i) and 0x1FFF)] = read((hdmaSource + i) and 0xFFFF).toByte()
        }
        hdmaSource = (hdmaSource + 0x10) and 0xFFFF
        hdmaDest = (hdmaDest + 0x10) and 0x1FFF
        if (--hdmaBlocks == 0) hdmaActive = false
    }

    override fun read(addr: Int): Int {
        val a = addr and 0xFFFF
        return when {
            a < 0x8000 -> cartridge.mbc.readRom(a)
            a < 0xA000 -> ppu.vram[ppu.vramBank * 0x2000 + (a - 0x8000)].toInt() and 0xFF
            a < 0xC000 -> cartridge.mbc.readRam(a)
            a < 0xFE00 -> wram[wramIndex(a)].toInt() and 0xFF
            a < 0xFEA0 -> ppu.oam[a - 0xFE00].toInt() and 0xFF
            a < 0xFF00 -> 0xFF
            a < 0xFF80 -> readIo(a)
            a < 0xFFFF -> hram[a - 0xFF80].toInt() and 0xFF
            else -> interrupts.enable
        }
    }

    override fun write(addr: Int, value: Int) {
        val a = addr and 0xFFFF
        val v = value and 0xFF
        when {
            a < 0x8000 -> cartridge.mbc.writeRom(a, v)
            a < 0xA000 -> ppu.vram[ppu.vramBank * 0x2000 + (a - 0x8000)] = v.toByte()
            a < 0xC000 -> cartridge.mbc.writeRam(a, v)
            a < 0xFE00 -> wram[wramIndex(a)] = v.toByte()
            a < 0xFEA0 -> ppu.oam[a - 0xFE00] = v.toByte()
            a < 0xFF00 -> {}
            a < 0xFF80 -> writeIo(a, v)
            a < 0xFFFF -> hram[a - 0xFF80] = v.toByte()
            else -> interrupts.enable = v
        }
    }

    private fun readIo(a: Int): Int = when (a) {
        0xFF00 -> joypad.read()
        0xFF01 -> serial.data
        0xFF02 -> serial.readControl()
        0xFF04 -> timer.div
        0xFF05 -> timer.tima
        0xFF06 -> timer.tma
        0xFF07 -> timer.tac or 0xF8
        0xFF0F -> interrupts.flags or 0xE0
        in 0xFF10..0xFF3F -> apu.read(a)
        0xFF40 -> ppu.lcdc
        0xFF41 -> ppu.readStat()
        0xFF42 -> ppu.scy
        0xFF43 -> ppu.scx
        0xFF44 -> ppu.ly
        0xFF45 -> ppu.lyc
        0xFF46 -> io[0x46]
        0xFF47 -> ppu.bgp
        0xFF48 -> ppu.obp0
        0xFF49 -> ppu.obp1
        0xFF4A -> ppu.wy
        0xFF4B -> ppu.wx
        0xFF4D -> if (cgb) (if (doubleSpeed) 0x80 else 0) or (if (speedPrepare) 1 else 0) or 0x7E else 0xFF
        0xFF4F -> if (cgb) 0xFE or ppu.vramBank else 0xFF
        0xFF55 -> if (cgb) (if (hdmaActive) (hdmaBlocks - 1) and 0x7F else 0xFF) else 0xFF
        0xFF68 -> if (cgb) ppu.bcps or 0x40 else 0xFF
        0xFF69 -> if (cgb) ppu.readBgPalette() else 0xFF
        0xFF6A -> if (cgb) ppu.ocps or 0x40 else 0xFF
        0xFF6B -> if (cgb) ppu.readObjPalette() else 0xFF
        0xFF70 -> if (cgb) 0xF8 or wramBank else 0xFF
        else -> io[a - 0xFF00]
    }

    private fun writeIo(a: Int, v: Int) {
        when (a) {
            0xFF00 -> joypad.write(v)
            0xFF01 -> serial.data = v
            0xFF02 -> serial.writeControl(v)
            0xFF04 -> timer.resetDiv()
            0xFF05 -> timer.tima = v
            0xFF06 -> timer.tma = v
            0xFF07 -> timer.tac = v and 7
            0xFF0F -> interrupts.flags = v or 0xE0
            in 0xFF10..0xFF3F -> apu.write(a, v)
            0xFF40 -> ppu.writeLcdc(v)
            0xFF41 -> ppu.writeStat(v)
            0xFF42 -> ppu.scy = v
            0xFF43 -> ppu.scx = v
            0xFF44 -> {} // LY is read-only
            0xFF45 -> ppu.lyc = v
            0xFF46 -> { io[0x46] = v; oamDma(v) }
            0xFF47 -> ppu.bgp = v
            0xFF48 -> ppu.obp0 = v
            0xFF49 -> ppu.obp1 = v
            0xFF4A -> ppu.wy = v
            0xFF4B -> ppu.wx = v
            0xFF4D -> if (cgb) speedPrepare = v and 1 != 0
            0xFF4F -> if (cgb) ppu.vramBank = v and 1
            0xFF51 -> hdmaSource = (v shl 8) or (hdmaSource and 0xFF)
            0xFF52 -> hdmaSource = (hdmaSource and 0xFF00) or (v and 0xF0)
            0xFF53 -> hdmaDest = ((v and 0x1F) shl 8) or (hdmaDest and 0xFF)
            0xFF54 -> hdmaDest = (hdmaDest and 0x1F00) or (v and 0xF0)
            0xFF55 -> if (cgb) writeHdma(v)
            0xFF68 -> if (cgb) ppu.bcps = v and 0xBF
            0xFF69 -> if (cgb) ppu.writeBgPalette(v)
            0xFF6A -> if (cgb) ppu.ocps = v and 0xBF
            0xFF6B -> if (cgb) ppu.writeObjPalette(v)
            0xFF70 -> if (cgb) wramBank = (v and 7).let { if (it == 0) 1 else it }
            else -> io[a - 0xFF00] = v
        }
    }

    /**
     * HDMA start (0xFF55). Bit 7 clear: copy everything now (general purpose DMA, modeled as
     * instantaneous). Bit 7 set: copy 16 bytes at each HBlank. Clearing bit 7 while an HBlank
     * transfer runs cancels it.
     */
    private fun writeHdma(v: Int) {
        if (hdmaActive && v and 0x80 == 0) { hdmaActive = false; return }
        hdmaBlocks = (v and 0x7F) + 1
        if (v and 0x80 != 0) hdmaActive = true else while (hdmaBlocks > 0) hdmaBlock()
    }

    /** OAM DMA: copies 160 bytes from (value << 8) into OAM. Modeled as instantaneous. */
    private fun oamDma(value: Int) {
        val src = value shl 8
        for (i in 0 until 0xA0) ppu.oam[i] = read(src + i).toByte()
    }
}
