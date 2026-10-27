package dev.kgameboy.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kgameboy.core.input.Button

/** Height of the controller area, used to grow the window when it is shown. */
val VIRTUAL_PAD_HEIGHT = 200.dp

private val BODY = Color(0xFFC9C6BE)      // grey plastic of the DMG
private val DARK = Color(0xFF2E2B3A)      // D-pad and Select/Start
private val DARK_PRESSED = Color(0xFF6B6880)
private val MAGENTA = Color(0xFF9B2257)   // A and B buttons
private val MAGENTA_PRESSED = Color(0xFFC9487E)

@Composable
private fun Key(
    controller: EmulatorController,
    button: Button,
    width: Dp,
    height: Dp,
    shape: Shape,
    color: Color,
    pressedColor: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var down by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (down) controller.press(button, false) } }
    Box(
        modifier.size(width, height).clip(shape)
            .background(if (down) pressedColor else color)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    down = true; controller.press(button, true)
                    do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
                    down = false; controller.press(button, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { if (label != null) Text(label, color = Color.White, fontSize = 10.sp) }
}

/**
 * Game Boy face below the screen: D-pad on the left, B and A on a diagonal on the right, and the
 * Select and Start pills tilted in the lower middle, like the real console.
 */
@Composable
fun VirtualPad(controller: EmulatorController) {
    Box(Modifier.fillMaxWidth().height(VIRTUAL_PAD_HEIGHT).background(BODY)) {
        Box(Modifier.size(360.dp, VIRTUAL_PAD_HEIGHT).align(Alignment.TopCenter)) {
            // D-pad: a plus made of four arms around the centre
            val arm = 38.dp
            val r = RoundedCornerShape(5.dp)
            Box(Modifier.offset(28.dp, 38.dp).size(arm * 3, arm * 3)) {
                Box(Modifier.offset(arm, arm).size(arm, arm).background(DARK))
                Key(controller, Button.UP, arm, arm, r, DARK, DARK_PRESSED, Modifier.offset(arm, 0.dp))
                Key(controller, Button.DOWN, arm, arm, r, DARK, DARK_PRESSED, Modifier.offset(arm, arm * 2))
                Key(controller, Button.LEFT, arm, arm, r, DARK, DARK_PRESSED, Modifier.offset(0.dp, arm))
                Key(controller, Button.RIGHT, arm, arm, r, DARK, DARK_PRESSED, Modifier.offset(arm * 2, arm))
            }
            // B (lower left) and A (upper right) of the pair
            Key(controller, Button.B, 48.dp, 48.dp, CircleShape, MAGENTA, MAGENTA_PRESSED, Modifier.offset(222.dp, 78.dp))
            Key(controller, Button.A, 48.dp, 48.dp, CircleShape, MAGENTA, MAGENTA_PRESSED, Modifier.offset(284.dp, 54.dp))
            Text("B", color = DARK, fontSize = 11.sp, modifier = Modifier.offset(238.dp, 130.dp))
            Text("A", color = DARK, fontSize = 11.sp, modifier = Modifier.offset(300.dp, 106.dp))
            // Select and Start pills
            val pill = RoundedCornerShape(50)
            Key(controller, Button.SELECT, 48.dp, 14.dp, pill, DARK, DARK_PRESSED, Modifier.offset(112.dp, 158.dp))
            Key(controller, Button.START, 48.dp, 14.dp, pill, DARK, DARK_PRESSED, Modifier.offset(172.dp, 158.dp))
            Text("SELECT", color = DARK, fontSize = 8.sp, modifier = Modifier.offset(114.dp, 174.dp))
            Text("START", color = DARK, fontSize = 8.sp, modifier = Modifier.offset(178.dp, 174.dp))
        }
    }
}
