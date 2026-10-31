package dev.kgameboy.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ArgsTest {
    @Test fun rom() = assertEquals(Command.Run("a.gb", null, null), parseArgs(listOf("a.gb")))
    @Test fun noArgs() = assertEquals(Command.Run(null, null, null), parseArgs(emptyList()))
    @Test fun help() = assertEquals(Command.Help, parseArgs(listOf("a.gb", "--help")))
    @Test fun version() = assertEquals(Command.Version, parseArgs(listOf("-V")))
    @Test fun headless() = assertEquals(
        Command.Run("a.gb", 10, "o.png"),
        parseArgs(listOf("a.gb", "--frames", "10", "--screenshot=o.png")),
    )
    @Test fun badFrames() = assertIs<Command.Invalid>(parseArgs(listOf("a.gb", "--frames", "x", "--screenshot", "o")))
    @Test fun missingValue() = assertIs<Command.Invalid>(parseArgs(listOf("a.gb", "--screenshot")))
    @Test fun unknown() = assertIs<Command.Invalid>(parseArgs(listOf("--nope")))
    @Test fun twoRoms() = assertIs<Command.Invalid>(parseArgs(listOf("a.gb", "b.gb")))
    @Test fun screenshotNeedsRom() = assertIs<Command.Invalid>(parseArgs(listOf("--screenshot", "o.png")))
    @Test fun framesNeedsScreenshot() = assertIs<Command.Invalid>(parseArgs(listOf("a.gb", "--frames", "5")))
}
