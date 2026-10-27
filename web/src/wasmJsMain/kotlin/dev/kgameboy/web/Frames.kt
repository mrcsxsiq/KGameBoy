package dev.kgameboy.web

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.kgameboy.core.video.Ppu
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** Converts the PPU's ARGB ints into an image. A fresh bitmap per frame avoids stale caches. */
fun frameImage(pixels: IntArray): ImageBitmap {
    val bytes = ByteArray(pixels.size * 4)
    for (i in pixels.indices) {
        val v = pixels[i]
        bytes[i * 4] = v.toByte()             // B
        bytes[i * 4 + 1] = (v shr 8).toByte() // G
        bytes[i * 4 + 2] = (v shr 16).toByte() // R
        bytes[i * 4 + 3] = 0xFF.toByte()      // A
    }
    val info = ImageInfo(Ppu.WIDTH, Ppu.HEIGHT, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    return Bitmap().apply { installPixels(info, bytes, Ppu.WIDTH * 4) }.asComposeImageBitmap()
}
