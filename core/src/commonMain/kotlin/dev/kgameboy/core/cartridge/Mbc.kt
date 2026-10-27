package dev.kgameboy.core.cartridge

/**
 * Memory bank controller. Cartridges are larger than the 32 KiB ROM window, so writes to the ROM
 * area (which cannot change ROM) are intercepted and used to select which 16 KiB bank is visible.
 */
abstract class Mbc(protected val rom: ByteArray, val ram: ByteArray) {
    protected val romBankCount = maxOf(1, rom.size / 0x4000)

    protected fun romAt(bank: Int, offset: Int): Int =
        rom[((bank % romBankCount) * 0x4000 + offset) % rom.size].toInt() and 0xFF

    abstract fun readRom(addr: Int): Int
    abstract fun writeRom(addr: Int, value: Int)
    abstract fun readRam(addr: Int): Int
    abstract fun writeRam(addr: Int, value: Int)

    protected fun ramIndex(bank: Int, addr: Int): Int =
        if (ram.isEmpty()) -1 else (bank * 0x2000 + (addr and 0x1FFF)) % ram.size
}

/** 32 KiB ROM, no banking. */
class RomOnly(rom: ByteArray, ram: ByteArray) : Mbc(rom, ram) {
    override fun readRom(addr: Int) = romAt(addr shr 14, addr and 0x3FFF)
    override fun writeRom(addr: Int, value: Int) {}
    override fun readRam(addr: Int): Int = ram.getOrNull(addr and 0x1FFF)?.toInt()?.and(0xFF) ?: 0xFF
    override fun writeRam(addr: Int, value: Int) {
        if (ram.isNotEmpty()) ram[(addr and 0x1FFF) % ram.size] = value.toByte()
    }
}

class Mbc1(rom: ByteArray, ram: ByteArray) : Mbc(rom, ram) {
    private var ramEnabled = false
    private var bank1 = 1      // 5-bit register, 0 is promoted to 1
    private var bank2 = 0      // 2-bit register: upper ROM bits or RAM bank
    private var advancedMode = false

    override fun readRom(addr: Int): Int {
        return if (addr < 0x4000) {
            romAt(if (advancedMode) bank2 shl 5 else 0, addr)
        } else {
            romAt((bank2 shl 5) or bank1, addr and 0x3FFF)
        }
    }

    override fun writeRom(addr: Int, value: Int) {
        when (addr shr 13) {
            0 -> ramEnabled = value and 0x0F == 0x0A
            1 -> bank1 = (value and 0x1F).let { if (it == 0) 1 else it }
            2 -> bank2 = value and 0x03
            3 -> advancedMode = value and 1 != 0
        }
    }

    override fun readRam(addr: Int): Int {
        if (!ramEnabled) return 0xFF
        val i = ramIndex(if (advancedMode) bank2 else 0, addr)
        return if (i < 0) 0xFF else ram[i].toInt() and 0xFF
    }

    override fun writeRam(addr: Int, value: Int) {
        if (!ramEnabled) return
        val i = ramIndex(if (advancedMode) bank2 else 0, addr)
        if (i >= 0) ram[i] = value.toByte()
    }
}

/** MBC3: 7-bit ROM bank, 4 RAM banks, and a real-time clock (registers are kept but do not tick). */
class Mbc3(rom: ByteArray, ram: ByteArray) : Mbc(rom, ram) {
    private var ramEnabled = false
    private var romBank = 1
    private var select = 0 // 0-3 RAM bank, 8-0xC RTC register
    private val rtc = IntArray(5)

    override fun readRom(addr: Int) =
        if (addr < 0x4000) romAt(0, addr) else romAt(romBank, addr and 0x3FFF)

    override fun writeRom(addr: Int, value: Int) {
        when (addr shr 13) {
            0 -> ramEnabled = value and 0x0F == 0x0A
            1 -> romBank = (value and 0x7F).let { if (it == 0) 1 else it }
            2 -> select = value
        }
    }

    override fun readRam(addr: Int): Int {
        if (!ramEnabled) return 0xFF
        if (select in 8..0xC) return rtc[select - 8]
        val i = ramIndex(select and 3, addr)
        return if (i < 0) 0xFF else ram[i].toInt() and 0xFF
    }

    override fun writeRam(addr: Int, value: Int) {
        if (!ramEnabled) return
        if (select in 8..0xC) { rtc[select - 8] = value; return }
        val i = ramIndex(select and 3, addr)
        if (i >= 0) ram[i] = value.toByte()
    }
}

/** MBC5: 9-bit ROM bank (bank 0 is selectable), up to 16 RAM banks. */
class Mbc5(rom: ByteArray, ram: ByteArray) : Mbc(rom, ram) {
    private var ramEnabled = false
    private var romBank = 1
    private var ramBank = 0

    override fun readRom(addr: Int) =
        if (addr < 0x4000) romAt(0, addr) else romAt(romBank, addr and 0x3FFF)

    override fun writeRom(addr: Int, value: Int) {
        when {
            addr < 0x2000 -> ramEnabled = value and 0x0F == 0x0A
            addr < 0x3000 -> romBank = (romBank and 0x100) or value
            addr < 0x4000 -> romBank = (romBank and 0xFF) or ((value and 1) shl 8)
            addr < 0x6000 -> ramBank = value and 0x0F
        }
    }

    override fun readRam(addr: Int): Int {
        if (!ramEnabled) return 0xFF
        val i = ramIndex(ramBank, addr)
        return if (i < 0) 0xFF else ram[i].toInt() and 0xFF
    }

    override fun writeRam(addr: Int, value: Int) {
        if (!ramEnabled) return
        val i = ramIndex(ramBank, addr)
        if (i >= 0) ram[i] = value.toByte()
    }
}
