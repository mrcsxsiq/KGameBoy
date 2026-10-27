package dev.kgameboy.web

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** One on-screen key. Pressed while any pointer is down on it; released on lift or cancel. */
@Composable
private fun PadKey(
    controller: WebController,
    button: Button,
    label: String,
    width: Dp,
    height: Dp,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    var down by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (down) controller.press(button, false) } }
    Box(
        Modifier.size(width, height).clip(shape)
            .background(if (down) Color(0xFF7A8C99) else Color(0xFF44505A))
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    controller.userGesture()
                    down = true; controller.press(button, true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    down = false; controller.press(button, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = if (label.length > 1 && label[0].isLetter()) 12.sp else 20.sp) }
}

/** D-pad on the left, A and B on the right, Select and Start in the middle. Multi-touch works. */
@Composable
fun TouchPad(controller: WebController, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(Color(0xFF1E242A)).padding(12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val k = 48.dp
        Box(Modifier.size(k * 3, k * 3)) {
            Box(Modifier.align(Alignment.TopCenter)) { PadKey(controller, Button.UP, "^", k, k) }
            Box(Modifier.align(Alignment.BottomCenter)) { PadKey(controller, Button.DOWN, "v", k, k) }
            Box(Modifier.align(Alignment.CenterStart)) { PadKey(controller, Button.LEFT, "<", k, k) }
            Box(Modifier.align(Alignment.CenterEnd)) { PadKey(controller, Button.RIGHT, ">", k, k) }
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            PadKey(controller, Button.SELECT, "SELECT", 72.dp, 32.dp)
            PadKey(controller, Button.START, "START", 72.dp, 32.dp)
        }
        Box(Modifier.size(120.dp, 90.dp)) {
            Box(Modifier.align(Alignment.BottomStart)) { PadKey(controller, Button.B, "B", 52.dp, 52.dp, CircleShape) }
            Box(Modifier.align(Alignment.TopEnd)) { PadKey(controller, Button.A, "A", 52.dp, 52.dp, CircleShape) }
        }
    }
}
