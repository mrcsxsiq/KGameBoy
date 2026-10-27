package dev.kgameboy.desktop

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.input.Button
import dev.kgameboy.core.video.VideoSink
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport

private const val FRAME_NANOS = 16_742_706L // 70224 cycles at 4.194304 MHz

/** One running ROM: owns the [GameBoy] and the thread that drives it. */
class EmulatorSession(
    val rom: File,
    private val video: VideoSink,
    private val audio: JavaSoundAudioSink,
    private val onFps: (Double) -> Unit,
) {
    val cartridge: Cartridge = Cartridge.load(FileRomSource(rom).load(), FileSaveStore(rom).read())
    private val gb = GameBoy(cartridge, video = video, audio = audio)
    private val events = ConcurrentLinkedQueue<Pair<Button, Boolean>>()
    @Volatile private var running = true
    @Volatile var paused = false

    private val thread = Thread(::loop, "emulator").apply { isDaemon = true }

    fun start() = thread.start()

    fun press(button: Button, pressed: Boolean) { events += button to pressed }

    private fun loop() {
        var next = System.nanoTime()
        var counted = 0
        var windowStart = System.nanoTime()
        while (running) {
            if (paused) { LockSupport.parkNanos(20_000_000); next = System.nanoTime(); continue }
            while (true) { val (b, p) = events.poll() ?: break; gb.setButton(b, p) }
            gb.runFrame()
            if (!audio.available) { // no audio device: pace with the clock instead
                next += FRAME_NANOS
                val wait = next - System.nanoTime()
                if (wait > 0) LockSupport.parkNanos(wait) else next = System.nanoTime()
            }
            if (++counted == 60) {
                val now = System.nanoTime()
                onFps(counted * 1e9 / (now - windowStart))
                counted = 0; windowStart = now
            }
        }
    }

    /** Stops the thread and writes battery RAM to disk. */
    fun stop() {
        running = false
        thread.join(1000)
        persist()
    }

    fun persist() { gb.saveData()?.let(FileSaveStore(rom)::write) }
}
