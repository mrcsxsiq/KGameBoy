package dev.kgameboy.core

import dev.kgameboy.core.audio.Apu
import dev.kgameboy.core.audio.AudioSink
import dev.kgameboy.core.cartridge.Cartridge
import dev.kgameboy.core.cpu.Cpu
import dev.kgameboy.core.input.Button
import dev.kgameboy.core.input.Joypad
import dev.kgameboy.core.interrupt.Interrupts
import dev.kgameboy.core.memory.Mmu
import dev.kgameboy.core.serial.Serial
import dev.kgameboy.core.timer.Timer
import dev.kgameboy.core.video.Ppu
import dev.kgameboy.core.video.VideoSink

/**
 * The whole machine. It owns every component and drives them from one clock: the CPU runs an
 * instruction, reports how many T-cycles it took, and the timer, PPU and APU advance by that many.
 */
class GameBoy(
    val cartridge: Cartridge,
    private val video: VideoSink,
    private val audio: AudioSink,
) {
    val interrupts = Interrupts()
    val timer = Timer(interrupts)
    val joypad = Joypad(interrupts)
    val serial = Serial(interrupts)
    val ppu = Ppu(interrupts)
    val apu = Apu()
    val mmu = Mmu(cartridge, interrupts, timer, joypad, serial, ppu, apu)
    val cpu = Cpu(mmu, interrupts)

    /** Total T-cycles executed since power-on. */
    var totalCycles = 0L
        private set

    /** Runs one instruction and advances all components. Returns the cycles it took. */
    fun step(): Int {
        val cycles = cpu.step()
        timer.tick(cycles)
        ppu.tick(cycles)
        apu.tick(cycles)
        totalCycles += cycles
        return cycles
    }

    /** Runs roughly one video frame (70224 cycles), then delivers the picture and audio. */
    fun runFrame() {
        var cycles = 0
        while (cycles < Ppu.FRAME_DOTS) cycles += step()
        ppu.frameReady = false
        video.onFrame(ppu.framebuffer)
        apu.drain(audio)
    }

    fun setButton(button: Button, pressed: Boolean) = joypad.set(button, pressed)

    /** Battery-backed cartridge RAM to persist, or null when the cartridge has none. */
    fun saveData(): ByteArray? = if (cartridge.hasBattery) cartridge.mbc.ram.copyOf() else null
}
