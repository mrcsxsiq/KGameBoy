package dev.kgameboy.desktop

import dev.kgameboy.core.video.Ppu
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel

/** Draws the 160x144 frame scaled by the largest integer factor that fits, with sharp pixels. */
class ScreenPanel(scale: Int) : JPanel() {
    private val image = BufferedImage(Ppu.WIDTH, Ppu.HEIGHT, BufferedImage.TYPE_INT_ARGB)

    init {
        preferredSize = Dimension(Ppu.WIDTH * scale, Ppu.HEIGHT * scale)
        background = Color.BLACK
        isFocusable = false
    }

    fun show(pixels: IntArray) {
        image.setRGB(0, 0, Ppu.WIDTH, Ppu.HEIGHT, pixels, 0, Ppu.WIDTH)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        val k = maxOf(1, minOf(width / Ppu.WIDTH, height / Ppu.HEIGHT))
        val w = Ppu.WIDTH * k; val h = Ppu.HEIGHT * k
        g2.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null)
    }
}
