package dev.kgameboy.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import dev.kgameboy.core.input.Button
import dev.kgameboy.core.video.Ppu
import java.awt.FileDialog
import java.io.File

private val IS_MAC = System.getProperty("os.name").lowercase().contains("mac")

private val KEYS = mapOf(
    Key.DirectionRight to Button.RIGHT, Key.DirectionLeft to Button.LEFT,
    Key.DirectionUp to Button.UP, Key.DirectionDown to Button.DOWN,
    Key.X to Button.A, Key.Z to Button.B,
    Key.Enter to Button.START, Key.Backspace to Button.SELECT,
    Key.ShiftLeft to Button.SELECT, Key.ShiftRight to Button.SELECT,
)

/** Shortcut with Cmd on macOS and Ctrl elsewhere. */
private fun shortcut(key: Key) = KeyShortcut(key, meta = IS_MAC, ctrl = !IS_MAC)

fun runApp(initialRom: File?) = application {
    val controller = remember { EmulatorController().also { c -> initialRom?.let(c::load) } }
    var scale by remember { mutableStateOf(4) }
    var showPad by remember { mutableStateOf(false) }
    val windowState = remember { WindowState(size = DpSize((Ppu.WIDTH * 4).dp, (Ppu.HEIGHT * 4).dp)) }
    var dialog by remember { mutableStateOf<String?>(null) }

    fun windowSize(s: Int, pad: Boolean) =
        DpSize((Ppu.WIDTH * s).dp, (Ppu.HEIGHT * s).dp + if (pad) VIRTUAL_PAD_HEIGHT else 0.dp)

    fun applyScale(s: Int) {
        scale = s
        windowState.size = windowSize(s, showPad)
    }

    fun setPad(visible: Boolean) {
        showPad = visible
        windowState.size = windowSize(scale, visible)
    }

    fun quit() { controller.shutdown(); exitApplication() }

    Window(
        onCloseRequest = ::quit,
        title = controller.title,
        state = windowState,
        onPreviewKeyEvent = { e -> handleKey(controller, e) },
    ) {
        fun openRom() {
            val chooser = FileDialog(window, "Open a Game Boy ROM", FileDialog.LOAD)
            chooser.setFilenameFilter { _, name -> name.endsWith(".gb", true) || name.endsWith(".gbc", true) }
            chooser.isVisible = true
            chooser.file?.let { controller.load(File(chooser.directory, it)) }
        }

        MenuBar {
            Menu("File") {
                Item("Open ROM…", shortcut = shortcut(Key.O), onClick = ::openRom)
                Item("Close ROM", shortcut = shortcut(Key.W), enabled = controller.isLoaded(), onClick = controller::close)
                if (!IS_MAC) { // macOS provides Quit in the application menu
                    Separator()
                    Item("Exit", onClick = ::quit)
                }
            }
            Menu("Emulation") {
                CheckboxItem(
                    "Pause", checked = controller.paused, enabled = controller.isLoaded(),
                    shortcut = shortcut(Key.P), onCheckedChange = controller::togglePause,
                )
                Item("Reset", shortcut = shortcut(Key.R), enabled = controller.isLoaded(), onClick = controller::reset)
            }
            Menu("View") {
                CheckboxItem("Virtual controller", checked = showPad, onCheckedChange = ::setPad)
                Menu("Window size") {
                    for (s in 1..6) RadioButtonItem("${s}x", selected = scale == s, onClick = { applyScale(s) })
                }
            }
            Menu("Help") {
                Item("Controls", onClick = { dialog = "controls" })
                Item("About KGameBoy", onClick = { dialog = "about" })
            }
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { Screen(controller) }
            if (showPad) VirtualPad(controller)
        }

        controller.error?.let { message ->
            InfoDialog("Cannot load ROM", message) { controller.error = null }
        }
        when (dialog) {
            "controls" -> InfoDialog(
                "Controls",
                "Arrow keys: D-pad\nX: A button\nZ: B button\nEnter: Start\nBackspace / Shift: Select",
            ) { dialog = null }
            "about" -> InfoDialog(
                "About KGameBoy",
                "A Game Boy (DMG) emulator written in Kotlin Multiplatform.",
            ) { dialog = null }
        }
    }
}

private fun handleKey(controller: EmulatorController, e: KeyEvent): Boolean {
    val button = KEYS[e.key] ?: return false
    when (e.type) {
        KeyEventType.KeyDown -> controller.press(button, true)
        KeyEventType.KeyUp -> controller.press(button, false)
        else -> return false
    }
    return true
}

/** The 160x144 frame, scaled by the largest integer factor that fits, with sharp pixels. */
@Composable
private fun Screen(controller: EmulatorController) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val image = controller.frame
        if (image == null) {
            Text("File > Open ROM…", color = Color(0xFF88C070))
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val k = maxOf(1, minOf((size.width / Ppu.WIDTH).toInt(), (size.height / Ppu.HEIGHT).toInt()))
                val w = Ppu.WIDTH * k
                val h = Ppu.HEIGHT * k
                drawImage(
                    image,
                    srcSize = IntSize(Ppu.WIDTH, Ppu.HEIGHT),
                    dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                    dstSize = IntSize(w, h),
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

@Composable
private fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
