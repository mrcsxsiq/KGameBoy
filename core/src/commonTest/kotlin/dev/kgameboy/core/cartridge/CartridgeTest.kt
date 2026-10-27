package dev.kgameboy.core.cartridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CartridgeTest {
    /** Builds a minimal ROM image with a valid header checksum. */
    private fun rom(title: String = "TEST", type: Int = 0, romCode: Int = 0, ramCode: Int = 0): ByteArray {
        val rom = ByteArray(0x8000)
        title.forEachIndexed { i, c -> rom[0x134 + i] = c.code.toByte() }
        rom[0x147] = type.toByte()
        rom[0x148] = romCode.toByte()
        rom[0x149] = ramCode.toByte()
        var x = 0
        for (i in 0x134..0x14C) x = (x - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        rom[0x14D] = x.toByte()
        return rom
    }

    @Test
    fun parsesTitleAndSizes() {
        val h = CartridgeHeader.parse(rom("TETRIS", type = 0x01, romCode = 2, ramCode = 3))
        assertEquals("TETRIS", h.title)
        assertEquals(0x01, h.cartridgeType)
        assertEquals(8, h.romBanks)
        assertEquals(32 * 1024, h.ramSizeBytes)
    }

    @Test
    fun acceptsValidChecksum() {
        assertTrue(CartridgeHeader.parse(rom()).headerChecksumValid)
    }

    @Test
    fun detectsInvalidChecksum() {
        val bad = rom().also { it[0x14D] = (it[0x14D] + 1).toByte() }
        assertFalse(CartridgeHeader.parse(bad).headerChecksumValid)
    }

    @Test
    fun rejectsTruncatedRom() {
        assertFailsWith<InvalidRomException> { CartridgeHeader.parse(ByteArray(0x100)) }
    }

    @Test
    fun rejectsUnknownRomSizeCode() {
        assertFailsWith<InvalidRomException> { CartridgeHeader.parse(rom(romCode = 0x20)) }
    }
}
