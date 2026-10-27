package dev.kgameboy.desktop

import dev.kgameboy.core.cartridge.RomSource
import dev.kgameboy.core.cartridge.SaveStore
import java.io.File

/** Reads a ROM image from a file. */
class FileRomSource(val file: File) : RomSource {
    override fun load(): ByteArray = file.readBytes()
}

/** Stores battery RAM in a `.sav` file next to the ROM. */
class FileSaveStore(rom: File) : SaveStore {
    private val file = File(rom.parentFile, rom.nameWithoutExtension + ".sav")
    override fun read(): ByteArray? = file.takeIf { it.exists() }?.readBytes()
    override fun write(data: ByteArray) = file.writeBytes(data)
}
