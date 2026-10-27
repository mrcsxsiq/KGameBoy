package dev.kgameboy.desktop

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import java.io.File

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "KGameBoy")

    var romPath: String? = null
    var frames = 0
    var screenshot: String? = null
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--frames" -> frames = args[++i].toInt()
            "--screenshot" -> screenshot = args[++i]
            else -> romPath = args[i]
        }
        i++
    }
    if (screenshot != null) {
        headless(romPath ?: error("A ROM path is required"), frames.coerceAtLeast(1), screenshot)
        return
    }
    runApp(romPath?.let(::File))
}

/** Runs [frames] frames without a window and writes the last one as a 4x PNG. */
private fun headless(romPath: String, frames: Int, out: String) {
    val rom = File(romPath)
    val gb = GameBoy(Cartridge.load(FileRomSource(rom).load(), FileSaveStore(rom).read()), video = {}, audio = { _, _ -> })
    repeat(frames) { gb.runFrame() }
    File(out).writeBytes(framePng(gb.ppu.framebuffer, scale = 4))
    println("Wrote $out after $frames frames")
}
