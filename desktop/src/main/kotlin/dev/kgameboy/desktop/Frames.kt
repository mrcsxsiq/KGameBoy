package dev.kgameboy.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import dev.kgameboy.core.video.Ppu
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/** Converts ARGB ints (as produced by the PPU) into a Skia bitmap, optionally scaled up by [scale]. */
private fun toBitmap(pixels: IntArray, scale: Int): Bitmap {
    val w = Ppu.WIDTH * scale
    val h = Ppu.HEIGHT * scale
    // BGRA_8888 bytes in memory are the little-endian bytes of an 0xAARRGGBB int.
    val buffer = ByteBuffer.allocate(w * h * 4).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
    for (y in 0 until h) {
        val row = (y / scale) * Ppu.WIDTH
        for (x in 0 until w) buffer.put(pixels[row + x / scale])
    }
    val bytes = ByteArray(w * h * 4)
    buffer.rewind()
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(buffer)
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    return Bitmap().apply { installPixels(info, bytes, w * 4) }
}

/** A fresh image per frame, so Skia never reuses a stale cached texture. */
fun frameImage(pixels: IntArray): ImageBitmap = toBitmap(pixels, 1).asComposeImageBitmap()

/** Encodes the frame as a PNG scaled by [scale]. */
fun framePng(pixels: IntArray, scale: Int): ByteArray =
    Image.makeFromBitmap(toBitmap(pixels, scale)).encodeToData(EncodedImageFormat.PNG)!!.bytes
