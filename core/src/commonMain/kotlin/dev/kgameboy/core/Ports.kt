package dev.kgameboy.core

/** Supplies the raw bytes of a ROM image. Each platform implements this its own way. */
fun interface RomSource {
    fun load(): ByteArray
}

/** Persists battery-backed cartridge RAM (the `.sav` file). */
interface SaveStore {
    fun read(): ByteArray?
    fun write(data: ByteArray)
}
