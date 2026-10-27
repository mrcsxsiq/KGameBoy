package dev.kgameboy.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import dev.kgameboy.core.video.Ppu

/** Shows the 160x144 frame, scaled by the largest integer factor that fits, with sharp pixels. */
class GameView(context: Context) : View(context) {
    private val bitmap = Bitmap.createBitmap(Ppu.WIDTH, Ppu.HEIGHT, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()

    init { setBackgroundColor(Color.BLACK) }

    /** Called from the emulator thread. */
    fun show(pixels: IntArray) {
        synchronized(bitmap) { bitmap.setPixels(pixels, 0, Ppu.WIDTH, 0, 0, Ppu.WIDTH, Ppu.HEIGHT) }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val k = maxOf(1, minOf(width / Ppu.WIDTH, height / Ppu.HEIGHT))
        val w = Ppu.WIDTH * k; val h = Ppu.HEIGHT * k
        dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        synchronized(bitmap) { canvas.drawBitmap(bitmap, null, dst, paint) }
    }
}
