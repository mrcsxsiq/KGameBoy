package dev.kgameboy.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import dev.kgameboy.core.GameBoy
import dev.kgameboy.core.cartridge.Cartridge
import java.io.File

class MainActivity : Activity() {
    private lateinit var game: GameView
    private var audio: AudioOutput? = null
    private var thread: EmulatorThread? = null
    private var saveFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = GameView(this)
        val controls = ControlsView(this) { b, p -> thread?.press(b, p) }

        val bar = LinearLayout(this).apply {
            setBackgroundColor(Color.rgb(0x14, 0x18, 0x1C))
            gravity = Gravity.CENTER
            addView(Button(this@MainActivity).apply { text = "Open ROM"; setOnClickListener { pickRom() } })
            addView(Button(this@MainActivity).apply { text = "Demo (dmg-acid2)"; setOnClickListener { loadDemo() } })
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(game, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.1f))
            addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        loadDemo()
    }

    private fun pickRom() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_ROM)
    }

    @Deprecated("Kept to avoid an AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (requestCode != REQUEST_ROM || resultCode != RESULT_OK) return
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        start(bytes, "rom-${uri.lastPathSegment.orEmpty().hashCode()}")
    }

    private fun loadDemo() = start(assets.open("dmg-acid2.gb").use { it.readBytes() }, "demo")

    private fun start(rom: ByteArray, saveName: String) {
        stopEmulator()
        val file = File(filesDir, "$saveName.sav").also { saveFile = it }
        val cartridge = try {
            Cartridge.load(rom, file.takeIf { it.exists() }?.readBytes())
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot load ROM: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        val out = AudioOutput().also { audio = it }
        thread = EmulatorThread(GameBoy(cartridge, video = { game.show(it) }, audio = out)).also { it.start() }
    }

    private fun stopEmulator() {
        thread?.let { t -> t.shutdown(); t.saveData()?.let { saveFile?.writeBytes(it) } }
        thread = null
        audio?.release(); audio = null
    }

    override fun onDestroy() { stopEmulator(); super.onDestroy() }

    private companion object { const val REQUEST_ROM = 1 }
}
