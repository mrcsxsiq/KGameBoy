package dev.kgameboy.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import dev.kgameboy.core.input.Button
import java.io.File

/** UI-facing state: the running ROM, the latest frame and the menu toggles. */
class EmulatorController {
    private val audio = JavaSoundAudioSink()
    private var session: EmulatorSession? = null

    var frame by mutableStateOf<ImageBitmap?>(null); private set
    var title by mutableStateOf("KGameBoy"); private set
    var paused by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    val loaded: Boolean get() = session != null
    private var baseTitle = "KGameBoy"
    private var tick by mutableStateOf(0) // forces `loaded` readers to recompose

    fun load(rom: File) {
        val next = try {
            EmulatorSession(rom, video = { frame = frameImage(it) }, audio = audio) { fps ->
                title = "$baseTitle  (%.1f FPS)".format(fps)
            }
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            return
        }
        session?.stop()
        audio.flush()
        session = next
        baseTitle = "KGameBoy - ${next.cartridge.header.title.ifBlank { rom.nameWithoutExtension }}"
        title = baseTitle
        paused = false
        tick++
        next.start()
    }

    fun reset() { session?.let { load(it.rom) } }

    fun close() {
        session?.stop(); session = null
        audio.flush()
        frame = null
        baseTitle = "KGameBoy"; title = baseTitle
        tick++
    }

    fun togglePause(value: Boolean) { paused = value; session?.paused = value }

    fun press(button: Button, pressed: Boolean) { session?.press(button, pressed) }

    fun shutdown() { session?.stop(); session = null }

    /** Reads [loaded] in a way Compose tracks. */
    fun isLoaded(): Boolean { tick; return loaded }
}
