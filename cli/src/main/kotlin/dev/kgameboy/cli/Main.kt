package dev.kgameboy.cli

import dev.kgameboy.desktop.runApp
import dev.kgameboy.desktop.runHeadless
import java.io.File
import java.util.Properties
import kotlin.system.exitProcess

private val USAGE = """
Usage: kgameboy [options] [<rom>]

Runs a Game Boy / Game Boy Color ROM in the KGameBoy emulator.
Without <rom>, a file dialog is shown.

Options:
  --frames N           Headless: run N frames (default 1) without a window
  --screenshot FILE    Headless: save the last frame as a 4x PNG
  -V, --version        Print version and exit
  -h, --help           Print this help and exit

Examples:
  kgameboy game.gb
  kgameboy game.gb --frames 120 --screenshot out.png
""".trimIndent()

private object Resources

private fun version(): String =
    Resources.javaClass.getResourceAsStream("/version.properties")
        ?.use { Properties().apply { load(it) }.getProperty("version") } ?: "unknown"

private fun fail(msg: String): Nothing {
    System.err.println("kgameboy: $msg")
    exitProcess(2)
}

fun main(args: Array<String>) {
    when (val cmd = parseArgs(args.toList())) {
        Command.Help -> println(USAGE)
        Command.Version -> println("kgameboy ${version()}")
        is Command.Invalid -> fail("${cmd.message}\nTry 'kgameboy --help'.")
        is Command.Run -> {
            val rom = cmd.rom?.let(::File)
            if (rom != null) {
                if (!rom.exists()) fail("ROM not found: ${cmd.rom}")
                if (!rom.isFile) fail("not a file: ${cmd.rom}")
                if (!rom.canRead()) fail("cannot read ROM: ${cmd.rom}")
            }
            try {
                if (cmd.screenshot != null) {
                    runHeadless(cmd.rom!!, cmd.frames ?: 1, cmd.screenshot)
                } else {
                    System.setProperty("apple.awt.application.name", "KGameBoy")
                    runApp(rom)
                }
            } catch (e: Exception) {
                fail(e.message ?: e::class.simpleName ?: "unexpected error")
            }
        }
    }
}
