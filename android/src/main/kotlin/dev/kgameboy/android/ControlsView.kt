package dev.kgameboy.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import dev.kgameboy.core.input.Button

/** On-screen D-pad plus A, B, Select and Start. Handles several simultaneous touches. */
class ControlsView(context: Context, private val onChange: (Button, Boolean) -> Unit) : View(context) {
    private class Key(val button: Button, val label: String, val round: Boolean) {
        val rect = RectF()
    }

    private val keys = listOf(
        Key(Button.UP, "▲", false), Key(Button.DOWN, "▼", false),
        Key(Button.LEFT, "◀", false), Key(Button.RIGHT, "▶", false),
        Key(Button.B, "B", true), Key(Button.A, "A", true),
        Key(Button.SELECT, "SELECT", false), Key(Button.START, "START", false),
    )
    private val down = HashSet<Button>()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }

    init { setBackgroundColor(Color.rgb(0x1E, 0x24, 0x2A)) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val u = minOf(w / 3.4f, h / 3.4f) // size of one D-pad cell
        val cx = u * 1.7f; val cy = h * 0.42f
        fun place(b: Button, x: Float, y: Float, s: Float) { keys.first { it.button == b }.rect.set(x - s / 2, y - s / 2, x + s / 2, y + s / 2) }
        place(Button.UP, cx, cy - u, u); place(Button.DOWN, cx, cy + u, u)
        place(Button.LEFT, cx - u, cy, u); place(Button.RIGHT, cx + u, cy, u)
        val ax = w - u * 1.2f; val bx = w - u * 2.6f
        place(Button.A, ax, cy - u * 0.4f, u * 1.15f); place(Button.B, bx, cy + u * 0.4f, u * 1.15f)
        val sy = h * 0.88f; val sw = u * 1.6f
        keys.first { it.button == Button.SELECT }.rect.set(w / 2f - sw - 12f, sy - u * 0.3f, w / 2f - 12f, sy + u * 0.3f)
        keys.first { it.button == Button.START }.rect.set(w / 2f + 12f, sy - u * 0.3f, w / 2f + sw + 12f, sy + u * 0.3f)
        text.textSize = u * 0.38f
    }

    override fun onDraw(canvas: Canvas) {
        for (k in keys) {
            fill.color = if (k.button in down) Color.rgb(0x7A, 0x8C, 0x99) else Color.rgb(0x44, 0x50, 0x5A)
            if (k.round) canvas.drawOval(k.rect, fill) else canvas.drawRoundRect(k.rect, 18f, 18f, fill)
            val small = k.label.length > 1 && k.label[0].isLetter()
            text.textSize = if (small) k.rect.height() * 0.32f else k.rect.height() * 0.45f
            canvas.drawText(k.label, k.rect.centerX(), k.rect.centerY() + text.textSize / 3, text)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val now = HashSet<Button>()
        if (e.actionMasked != MotionEvent.ACTION_UP && e.actionMasked != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if (e.actionMasked == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                for (k in keys) {
                    // Slightly enlarged hit box so thumbs between keys still register.
                    val r = RectF(k.rect).apply { inset(-12f, -12f) }
                    if (r.contains(e.getX(i), e.getY(i))) now += k.button
                }
            }
        }
        for (b in down - now) onChange(b, false)
        for (b in now - down) onChange(b, true)
        down.clear(); down.addAll(now)
        invalidate()
        return true
    }
}
