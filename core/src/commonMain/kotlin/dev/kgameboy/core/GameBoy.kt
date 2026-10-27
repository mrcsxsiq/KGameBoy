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
    /** True when the cartridge supports Game Boy Color; the machine then behaves like a CGB. */
    val cgb = cartridge.header.cgbSupported
    val ppu = Ppu(interrupts, cgb)
    val apu = Apu()
    val mmu = Mmu(cartridge, interrupts, timer, joypad, serial, ppu, apu, cgb)
    val cpu = Cpu(mmu, interrupts)

    init {
        if (cgb) { // registers left behind by the CGB boot ROM
            cpu.a = 0x11; cpu.f = 0x80; cpu.b = 0x00; cpu.c = 0x00
            cpu.d = 0xFF; cpu.e = 0x56; cpu.h = 0x00; cpu.l = 0x0D
        }
        cpu.onStop = mmu::trySpeedSwitch
    }

    // Dots (PPU/APU cycles) produced by the last step, and CPU cycles not yet turned into dots.
    private var lastDots = 0
    private var pendingCpuCycles = 0

    /** Total T-cycles executed since power-on. */
    var totalCycles = 0L
        private set

    /** Runs one instruction and advances all components. Returns the cycles it took. */
    fun step(): Int {
        val cycles = cpu.step()
        // In double speed the CPU and timer run twice as fast, while the PPU and APU keep their
        // pace, so they receive half as many cycles (in multiples of 4 dots).
        val dots = if (mmu.doubleSpeed) {
            pendingCpuCycles += cycles
            val whole = pendingCpuCycles - pendingCpuCycles % 8
            pendingCpuCycles -= whole
            whole / 2
        } else {
            pendingCpuCycles = 0
            cycles
        }
        timer.tick(cycles)
        if (dots > 0) { ppu.tick(dots); apu.tick(dots) }
        lastDots = dots
        totalCycles += cycles
        return cycles
    }

    /** Runs one video frame (70224 dots, which is twice as many CPU cycles in double speed), then delivers the picture and audio. */
    fun runFrame() {
        var dots = 0
        while (dots < Ppu.FRAME_DOTS) { step(); dots += lastDots }
        ppu.frameReady = false
        video.onFrame(ppu.framebuffer)
        apu.drain(audio)
    }

    fun setButton(button: Button, pressed: Boolean) = joypad.set(button, pressed)

    /** Battery-backed cartridge RAM to persist, or null when the cartridge has none. */
    fun saveData(): ByteArray? = if (cartridge.hasBattery) cartridge.mbc.ram.copyOf() else null
}
