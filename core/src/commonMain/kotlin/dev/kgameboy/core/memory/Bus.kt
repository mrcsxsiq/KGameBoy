package dev.kgameboy.core.memory

/** What the CPU sees: a 16-bit address space of bytes. */
interface Bus {
    fun read(addr: Int): Int
    fun write(addr: Int, value: Int)
}
