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
) : Bus {
    private val wram = ByteArray(0x2000)
    private val hram = ByteArray(0x80)
    private val io = IntArray(0x80) // registers without a dedicated component (e.g. FF50)

    override fun read(addr: Int): Int {
        val a = addr and 0xFFFF
        return when {
            a < 0x8000 -> cartridge.mbc.readRom(a)
            a < 0xA000 -> ppu.vram[a - 0x8000].toInt() and 0xFF
            a < 0xC000 -> cartridge.mbc.readRam(a)
            a < 0xE000 -> wram[a - 0xC000].toInt() and 0xFF
            a < 0xFE00 -> wram[a - 0xE000].toInt() and 0xFF
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
            a < 0xA000 -> ppu.vram[a - 0x8000] = v.toByte()
            a < 0xC000 -> cartridge.mbc.writeRam(a, v)
            a < 0xE000 -> wram[a - 0xC000] = v.toByte()
            a < 0xFE00 -> wram[a - 0xE000] = v.toByte()
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
            else -> io[a - 0xFF00] = v
        }
    }

    /** OAM DMA: copies 160 bytes from (value << 8) into OAM. Modeled as instantaneous. */
    private fun oamDma(value: Int) {
        val src = value shl 8
        for (i in 0 until 0xA0) ppu.oam[i] = read(src + i).toByte()
    }
}
