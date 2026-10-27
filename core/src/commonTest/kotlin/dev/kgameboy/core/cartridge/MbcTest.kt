package dev.kgameboy.core.cartridge

import kotlin.test.Test
import kotlin.test.assertEquals

class MbcTest {
    /** A ROM where every byte of bank N equals N, so the visible bank is easy to read back. */
    private fun bankedRom(banks: Int) = ByteArray(banks * 0x4000) { (it / 0x4000).toByte() }

    @Test
    fun romOnlyMapsTwoFixedBanks() {
        val mbc = RomOnly(bankedRom(2), ByteArray(0))
        assertEquals(0, mbc.readRom(0x0000))
        assertEquals(1, mbc.readRom(0x4000))
    }

    @Test
    fun mbc1SwitchesSwitchableBank() {
        val mbc = Mbc1(bankedRom(8), ByteArray(0))
        assertEquals(1, mbc.readRom(0x4000))
        mbc.writeRom(0x2000, 5)
        assertEquals(5, mbc.readRom(0x4000))
    }

    @Test
    fun mbc1PromotesBankZeroToOne() {
        val mbc = Mbc1(bankedRom(8), ByteArray(0))
        mbc.writeRom(0x2000, 0)
        assertEquals(1, mbc.readRom(0x4000))
    }

    @Test
    fun mbc1RamNeedsEnableValue() {
        val mbc = Mbc1(bankedRom(2), ByteArray(0x2000))
        mbc.writeRam(0xA000, 0x42)
        assertEquals(0xFF, mbc.readRam(0xA000)) // disabled: reads open bus, write ignored
        mbc.writeRom(0x0000, 0x0A)
        mbc.writeRam(0xA000, 0x42)
        assertEquals(0x42, mbc.readRam(0xA000))
    }

    @Test
    fun mbc3SelectsSevenBitRomBank() {
        val mbc = Mbc3(bankedRom(128), ByteArray(0))
        mbc.writeRom(0x2000, 0x7F)
        assertEquals(0x7F, mbc.readRom(0x4000))
    }

    @Test
    fun mbc5BankZeroIsSelectableAndNineBitBanksWork() {
        val mbc = Mbc5(bankedRom(2), ByteArray(0))
        mbc.writeRom(0x2000, 0)
        assertEquals(0, mbc.readRom(0x4000))
        val big = Mbc5(ByteArray(0x4000 * 512) { 0 }.also { it[0x4000 * 300] = 7 }, ByteArray(0))
        big.writeRom(0x2000, 300 and 0xFF)
        big.writeRom(0x3000, 1)
        assertEquals(7, big.readRom(0x4000))
    }
}
