@file:OptIn(ExperimentalComposeUiApi::class)

package dev.kgameboy.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import dev.kgameboy.core.input.Button
import dev.kgameboy.core.video.Ppu
import kotlinx.browser.document

private const val FRAME_MS = 1000.0 * 70224 / 4194304 // 16.74 ms per emulated frame
private val GREEN = Color(0xFF88C070)

private val KEYS = mapOf(
    Key.DirectionRight to Button.RIGHT, Key.DirectionLeft to Button.LEFT,
    Key.DirectionUp to Button.UP, Key.DirectionDown to Button.DOWN,
    Key.X to Button.A, Key.Z to Button.B,
    Key.Enter to Button.START, Key.Backspace to Button.SELECT,
    Key.ShiftLeft to Button.SELECT, Key.ShiftRight to Button.SELECT,
)

fun main() {
    ComposeViewport(document.body!!) { WebApp() }
}

@Composable
private fun WebApp() {
    val controller = remember { WebController() }
    val focus = remember { FocusRequester() }

    // Run emulated frames against real time, independent of the display refresh rate.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        var last = 0L
        var owed = 0.0
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) owed += minOf((now - last) / 1_000_000.0, 100.0)
                last = now
                var frames = 0
                while (owed >= FRAME_MS && frames < 3) { controller.runFrame(); owed -= FRAME_MS; frames++ }
            }
        }
    }

    fun act(block: () -> Unit) { controller.userGesture(); block(); focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0F1419)).focusRequester(focus).focusable()
            .onPreviewKeyEvent { e -> handleKey(controller, e) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(onClick = { act { pickRom { name, bytes -> controller.load(bytes, name) } } }) { Text("Open ROM…") }
            MenuButton(onClick = { act { fetchBytes("dmg-acid2.gb") { controller.load(it, "dmg-acid2") } } }) { Text("Demo") }
            MenuButton(enabled = controller.loaded, onClick = { act { controller.paused = !controller.paused } }) {
                Text(if (controller.paused) "Resume" else "Pause")
            }
            MenuButton(enabled = controller.loaded, onClick = { act(controller::reset) }) { Text("Reset") }
            Text(controller.status, color = GREEN)
        }
        Screen(controller, Modifier.weight(1f))
        TouchPad(controller)
        Text(
            "Keyboard: arrows, X = A, Z = B, Enter = Start, Backspace/Shift = Select. Touch pad works too.",
            color = GREEN, modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GREEN, disabledContentColor = Color(0xFF4A5A44)),
    ) { content() }
}

private fun handleKey(controller: WebController, e: KeyEvent): Boolean {
    val button = KEYS[e.key] ?: return false
    controller.userGesture()
    when (e.type) {
        KeyEventType.KeyDown -> controller.press(button, true)
        KeyEventType.KeyUp -> controller.press(button, false)
        else -> return false
    }
    return true
}

/** The 160x144 frame, scaled by the largest integer factor that fits, with sharp pixels. */
@Composable
private fun Screen(controller: WebController, modifier: Modifier) {
    Box(modifier.fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
        val image = controller.frame
        if (image != null) {
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
