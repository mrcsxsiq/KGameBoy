package dev.kgameboy.core.cartridge

/** Thrown when a ROM image cannot be parsed as a Game Boy cartridge. */
class InvalidRomException(message: String) : Exception(message)

/** Parsed cartridge header (bytes 0x0100-0x014F of the ROM). */
class CartridgeHeader(
    val title: String,
    val cartridgeType: Int,
    val romBanks: Int,
    val ramSizeBytes: Int,
    val headerChecksum: Int,
    val headerChecksumValid: Boolean,
    /** Byte at 0x0143: 0x80 = works on Game Boy Color and DMG, 0xC0 = Game Boy Color only. */
    val cgbFlag: Int = 0,
) {
    /** True when the cartridge asks for Game Boy Color features. */
    val cgbSupported: Boolean get() = cgbFlag and 0x80 != 0

    companion object {
        const val HEADER_END = 0x014F
        private const val TITLE_START = 0x0134
        private const val TITLE_END = 0x0143
        private const val CGB_FLAG = 0x0143
        private const val TYPE = 0x0147
        private const val ROM_SIZE = 0x0148
        private const val RAM_SIZE = 0x0149
        private const val CHECKSUM = 0x014D

        /** RAM sizes indexed by the header code at 0x0149. */
        private val RAM_SIZES = intArrayOf(0, 0, 8 * 1024, 32 * 1024, 128 * 1024, 64 * 1024)

        fun parse(rom: ByteArray): CartridgeHeader {
            if (rom.size <= HEADER_END) {
                throw InvalidRomException("ROM too small to contain a header: ${rom.size} bytes")
            }
            fun u8(i: Int) = rom[i].toInt() and 0xFF

            val cgbFlag = u8(CGB_FLAG)
            // On CGB cartridges byte 0x0143 is a flag, not part of the title.
            val titleEnd = if (cgbFlag and 0x80 != 0) TITLE_END - 1 else TITLE_END
            val title = buildString {
                for (i in TITLE_START..titleEnd) {
                    val c = u8(i)
                    if (c == 0) break
                    append(c.toChar())
                }
            }

            // Header checksum: x = 0; for addr in 0x134..0x14C: x = x - rom[addr] - 1
            var x = 0
            for (i in TITLE_START..0x014C) x = (x - u8(i) - 1) and 0xFF

            val romSizeCode = u8(ROM_SIZE)
            if (romSizeCode > 0x08) throw InvalidRomException("Unknown ROM size code: $romSizeCode")
            val ramCode = u8(RAM_SIZE)
            if (ramCode >= RAM_SIZES.size) throw InvalidRomException("Unknown RAM size code: $ramCode")

            return CartridgeHeader(
                title = title,
                cartridgeType = u8(TYPE),
                romBanks = 2 shl romSizeCode, // 32 KiB << code, in 16 KiB banks
                ramSizeBytes = RAM_SIZES[ramCode],
                headerChecksum = u8(CHECKSUM),
                headerChecksumValid = x == u8(CHECKSUM),
                cgbFlag = cgbFlag,
            )
        }
    }
}

/** A parsed ROM image together with the memory bank controller its header asks for. */
class Cartridge(val header: CartridgeHeader, val mbc: Mbc) {
    val hasBattery: Boolean get() = header.cartridgeType in BATTERY_TYPES

    companion object {
        private val BATTERY_TYPES = setOf(0x03, 0x06, 0x09, 0x0D, 0x0F, 0x10, 0x13, 0x1B, 0x1E)

        fun load(rom: ByteArray, save: ByteArray? = null): Cartridge {
            val header = CartridgeHeader.parse(rom)
            val ram = ByteArray(header.ramSizeBytes)
            if (save != null) save.copyInto(ram, endIndex = minOf(save.size, ram.size))
            val mbc = when (header.cartridgeType) {
                0x00, 0x08, 0x09 -> RomOnly(rom, ram)
                0x01, 0x02, 0x03 -> Mbc1(rom, ram)
                0x0F, 0x10, 0x11, 0x12, 0x13 -> Mbc3(rom, ram)
                0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> Mbc5(rom, ram)
                else -> throw InvalidRomException("Unsupported cartridge type 0x${header.cartridgeType.toString(16)}")
            }
            return Cartridge(header, mbc)
        }
    }
}
