package dev.kgameboy.desktop

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.input.Button
import dev.kgameboy.core.video.Ppu
import java.awt.FileDialog
import java.awt.Frame
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private const val FRAME_NANOS = 16_742_706L // 70224 cycles at 4.194304 MHz

private val KEYS = mapOf(
    KeyEvent.VK_RIGHT to Button.RIGHT, KeyEvent.VK_LEFT to Button.LEFT,
    KeyEvent.VK_UP to Button.UP, KeyEvent.VK_DOWN to Button.DOWN,
    KeyEvent.VK_X to Button.A, KeyEvent.VK_Z to Button.B,
    KeyEvent.VK_ENTER to Button.START, KeyEvent.VK_BACK_SPACE to Button.SELECT,
    KeyEvent.VK_SHIFT to Button.SELECT,
)

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
    if (screenshot != null) { headless(romPath ?: error("A ROM path is required"), frames.coerceAtLeast(1), screenshot); return }

    val rom = romPath?.let(::File) ?: chooseRom() ?: exitProcess(0)
    SwingUtilities.invokeLater { openWindow(rom) }
}

private fun chooseRom(): File? {
    val dialog = FileDialog(null as Frame?, "Open a Game Boy ROM", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.endsWith(".gb") || name.endsWith(".gbc") }
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

private fun loadCartridge(rom: File): Cartridge = Cartridge.load(FileRomSource(rom).load(), FileSaveStore(rom).read())

/** Runs [frames] frames without a window and writes the last one as a PNG. */
private fun headless(romPath: String, frames: Int, out: String) {
    val gb = GameBoy(loadCartridge(File(romPath)), video = {}, audio = { _, _ -> })
    repeat(frames) { gb.runFrame() }
    val image = BufferedImage(Ppu.WIDTH, Ppu.HEIGHT, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, Ppu.WIDTH, Ppu.HEIGHT, gb.ppu.framebuffer, 0, Ppu.WIDTH)
    val scaled = BufferedImage(Ppu.WIDTH * 4, Ppu.HEIGHT * 4, BufferedImage.TYPE_INT_ARGB)
    scaled.createGraphics().apply { drawImage(image, 0, 0, scaled.width, scaled.height, null); dispose() }
    ImageIO.write(scaled, "png", File(out))
    println("Wrote $out after $frames frames")
}

private fun openWindow(rom: File) {
    val cartridge = loadCartridge(rom)
    val screen = ScreenPanel(scale = 4)
    val audio = SwingAudioSink()
    val gb = GameBoy(cartridge, video = { screen.show(it) }, audio = audio)
    val saves = FileSaveStore(rom)
    val title = cartridge.header.title.ifBlank { rom.nameWithoutExtension }

    val events = ConcurrentLinkedQueue<Pair<Button, Boolean>>()
    val frame = JFrame("KGameBoy - $title").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        add(screen)
        pack()
        setLocationRelativeTo(null)
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) { KEYS[e.keyCode]?.let { events += it to true } }
            override fun keyReleased(e: KeyEvent) { KEYS[e.keyCode]?.let { events += it to false } }
        })
        isVisible = true
    }

    fun persist() { gb.saveData()?.let(saves::write) }
    Runtime.getRuntime().addShutdownHook(Thread { persist() })

    Thread({
        var next = System.nanoTime()
        var counted = 0
        var windowStart = System.nanoTime()
        while (true) {
            while (true) { val (b, p) = events.poll() ?: break; gb.setButton(b, p) }
            gb.runFrame()
            if (!audio.available) { // no audio device: pace with the clock instead
                next += FRAME_NANOS
                val wait = next - System.nanoTime()
                if (wait > 0) LockSupport.parkNanos(wait) else next = System.nanoTime()
            }
            if (++counted == 60) {
                val now = System.nanoTime()
                val fps = counted * 1e9 / (now - windowStart)
                SwingUtilities.invokeLater { frame.title = "KGameBoy - $title  (%.1f FPS)".format(fps) }
                counted = 0; windowStart = now
            }
        }
    }, "emulator").apply { isDaemon = true; start() }
}
