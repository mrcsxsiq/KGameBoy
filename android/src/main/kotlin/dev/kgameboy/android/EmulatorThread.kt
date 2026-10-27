package dev.kgameboy.android

import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.input.Button
import java.util.concurrent.ConcurrentLinkedQueue

/** Runs the emulator on its own thread; touch events are queued and applied between frames. */
class EmulatorThread(private val gb: GameBoy) : Thread("emulator") {
    @Volatile private var running = true
    private val events = ConcurrentLinkedQueue<Pair<Button, Boolean>>()

    fun press(button: Button, pressed: Boolean) { events += button to pressed }

    fun shutdown() { running = false; join(500) }

    override fun run() {
        while (running) {
            while (true) { val (b, p) = events.poll() ?: break; gb.setButton(b, p) }
            gb.runFrame()
        }
    }

    fun saveData(): ByteArray? = gb.saveData()
}
