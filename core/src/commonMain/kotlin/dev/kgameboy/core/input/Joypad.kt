package dev.kgameboy.core.input

import dev.kgameboy.core.interrupt.Interrupts

enum class Button { RIGHT, LEFT, UP, DOWN, A, B, SELECT, START }

/**
 * Register P1 (0xFF00): a 2x4 matrix. Writing bits 4/5 (active low) selects the direction or the
 * action row; reading bits 0-3 returns the keys of the selected rows, 0 meaning pressed.
 */
class Joypad(private val interrupts: Interrupts) {
    private var select = 0x30
    private var directions = 0 // bit set = pressed
    private var actions = 0

    fun write(value: Int) {
        select = value and 0x30
    }

    fun read(): Int {
        var low = 0x0F
        if (select and 0x10 == 0) low = low and directions.inv()
        if (select and 0x20 == 0) low = low and actions.inv()
        return 0xC0 or select or (low and 0x0F)
    }

    fun set(button: Button, pressed: Boolean) {
        val index = button.ordinal
        val isDirection = index < 4
        val bit = 1 shl (index and 3)
        val before = read() and 0x0F
        if (isDirection) directions = if (pressed) directions or bit else directions and bit.inv()
        else actions = if (pressed) actions or bit else actions and bit.inv()
        // A line going from 1 to 0 on a selected row raises the joypad interrupt.
        if (before and (read() and 0x0F).inv() != 0) interrupts.request(Interrupts.JOYPAD)
    }
}
