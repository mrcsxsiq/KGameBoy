package dev.kgameboy.cli

/** Result of parsing the command line. */
sealed interface Command {
    data object Help : Command
    data object Version : Command
    data class Run(val rom: String?, val frames: Int?, val screenshot: String?) : Command
    data class Invalid(val message: String) : Command
}

private fun parseFrames(v: String): Int? = v.toIntOrNull()?.takeIf { it > 0 }

fun parseArgs(args: List<String>): Command {
    var rom: String? = null
    var frames: Int? = null
    var screenshot: String? = null
    var i = 0
    while (i < args.size) {
        val a = args[i]
        when {
            a == "-h" || a == "--help" -> return Command.Help
            a == "-V" || a == "--version" -> return Command.Version
            a == "--frames" || a == "--screenshot" -> {
                val v = args.getOrNull(i + 1) ?: return Command.Invalid("$a requires a value")
                if (a == "--frames") {
                    frames = parseFrames(v) ?: return Command.Invalid("--frames expects a positive integer, got '$v'")
                } else {
                    screenshot = v
                }
                i++
            }
            a.startsWith("--frames=") -> {
                val v = a.substringAfter('=')
                frames = parseFrames(v) ?: return Command.Invalid("--frames expects a positive integer, got '$v'")
            }
            a.startsWith("--screenshot=") -> screenshot = a.substringAfter('=')
            a.startsWith("-") -> return Command.Invalid("unknown option '$a'")
            rom != null -> return Command.Invalid("only one ROM may be given (got '$rom' and '$a')")
            else -> rom = a
        }
        i++
    }
    if (screenshot != null && rom == null) return Command.Invalid("--screenshot requires a ROM path")
    if (frames != null && screenshot == null) return Command.Invalid("--frames requires --screenshot")
    return Command.Run(rom, frames, screenshot)
}
