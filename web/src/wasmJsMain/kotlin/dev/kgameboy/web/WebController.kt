package dev.kgameboy.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.input.Button

/** Emulator state for the page. The browser is single-threaded, so frames run on the UI loop. */
class WebController {
    private var gb: GameBoy? = null
    private var rom: ByteArray? = null
    private var audio: WebAudio? = null
    private var frameDue = false

    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var status by mutableStateOf("Open a ROM or load the demo.")
    var paused by mutableStateOf(false)
    var loaded by mutableStateOf(false); private set

    /** Audio can only start after a user gesture, so it is created lazily on the first one. */
    fun userGesture() {
        val a = audio ?: WebAudio().also { audio = it }
        a.resume()
    }

    fun load(bytes: ByteArray, name: String) {
        val cartridge = try {
            Cartridge.load(bytes)
        } catch (e: Exception) {
            status = "Cannot load $name: ${e.message}"
            return
        }
        rom = bytes
        gb = GameBoy(cartridge, video = { frameDue = true }, audio = { samples, n -> audio?.push(samples, n) })
        paused = false
        loaded = true
        status = cartridge.header.title.ifBlank { name }
    }

    fun reset() { rom?.let { load(it, status) } }

    fun press(button: Button, pressed: Boolean) { gb?.setButton(button, pressed) }

    /** Runs one frame if the game is running and publishes the picture. */
    fun runFrame() {
        val machine = gb ?: return
        if (paused) return
        machine.runFrame()
        if (frameDue) { frameDue = false; frame = frameImage(machine.ppu.framebuffer) }
    }
}
