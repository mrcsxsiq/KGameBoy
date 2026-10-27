package dev.kgameboy.core.cpu

import dev.kgameboy.core.interrupt.Interrupts
import dev.kgameboy.core.memory.Bus

/**
 * Sharp LR35902, the Game Boy CPU (a Z80/8080 hybrid).
 *
 * [step] runs one fetch-decode-execute cycle (or services an interrupt) and returns the number of
 * T-cycles consumed, so the caller can advance every other component by the same amount.
 */
class Cpu(private val bus: Bus, private val interrupts: Interrupts) {
    // 8-bit registers
    var a = 0x01; var b = 0x00; var c = 0x13; var d = 0x00
    var e = 0xD8; var h = 0x01; var l = 0x4D
    var f = 0xB0 // flags: Z N H C in bits 7..4
    var sp = 0xFFFE
    var pc = 0x0100

    var ime = false
    var halted = false
    private var eiPending = false
    private var haltBug = false

    var bc: Int
        get() = (b shl 8) or c
        set(v) { b = (v shr 8) and 0xFF; c = v and 0xFF }
    var de: Int
        get() = (d shl 8) or e
        set(v) { d = (v shr 8) and 0xFF; e = v and 0xFF }
    var hl: Int
        get() = (h shl 8) or l
        set(v) { h = (v shr 8) and 0xFF; l = v and 0xFF }
    var af: Int
        get() = (a shl 8) or f
        set(v) { a = (v shr 8) and 0xFF; f = v and 0xF0 }

    private fun flag(mask: Int) = f and mask != 0
    private fun setFlag(mask: Int, on: Boolean) { f = if (on) f or mask else f and mask.inv() }
    private fun setFlags(z: Boolean, n: Boolean, hc: Boolean, cy: Boolean) {
        f = (if (z) Z else 0) or (if (n) N else 0) or (if (hc) H else 0) or (if (cy) C else 0)
    }

    // ---- memory helpers ----
    private fun read(addr: Int) = bus.read(addr and 0xFFFF)
    private fun write(addr: Int, v: Int) = bus.write(addr and 0xFFFF, v and 0xFF)
    private fun fetch(): Int {
        val v = read(pc)
        if (haltBug) haltBug = false else pc = (pc + 1) and 0xFFFF
        return v
    }
    private fun fetch16(): Int { val lo = fetch(); return lo or (fetch() shl 8) }
    private fun push(v: Int) {
        sp = (sp - 1) and 0xFFFF; write(sp, v shr 8)
        sp = (sp - 1) and 0xFFFF; write(sp, v and 0xFF)
    }
    private fun pop(): Int {
        val lo = read(sp); sp = (sp + 1) and 0xFFFF
        val hi = read(sp); sp = (sp + 1) and 0xFFFF
        return lo or (hi shl 8)
    }

    // register index 0..7 = B C D E H L (HL) A
    private fun getR(i: Int): Int = when (i) {
        0 -> b; 1 -> c; 2 -> d; 3 -> e; 4 -> h; 5 -> l; 6 -> read(hl); else -> a
    }
    private fun setR(i: Int, v: Int) {
        val x = v and 0xFF
        when (i) { 0 -> b = x; 1 -> c = x; 2 -> d = x; 3 -> e = x; 4 -> h = x; 5 -> l = x; 6 -> write(hl, x); else -> a = x }
    }

    private fun getRR(i: Int): Int = when (i) { 0 -> bc; 1 -> de; 2 -> hl; else -> sp }
    private fun setRR(i: Int, v: Int) {
        val x = v and 0xFFFF
        when (i) { 0 -> bc = x; 1 -> de = x; 2 -> hl = x; else -> sp = x }
    }

    private fun condition(i: Int): Boolean = when (i) {
        0 -> !flag(Z); 1 -> flag(Z); 2 -> !flag(C); else -> flag(C)
    }

    /** Executes one instruction (or interrupt dispatch). Returns T-cycles used. */
    fun step(): Int {
        val pending = interrupts.pending()
        if (halted) {
            if (pending != 0) halted = false else return 4
        }
        if (ime && pending != 0) return serviceInterrupt(pending)

        val enableIme = eiPending
        val cycles = execute(fetch())
        if (enableIme) { ime = true; eiPending = false }
        return cycles
    }

    private fun serviceInterrupt(pending: Int): Int {
        val bit = pending.countTrailingZeroBits()
        ime = false
        interrupts.flags = interrupts.flags and (1 shl bit).inv()
        push(pc)
        pc = 0x40 + bit * 8
        return 20
    }

    // ---- ALU ----
    private fun alu(op: Int, v: Int) {
        when (op) {
            0 -> { val r = a + v; setFlags(r and 0xFF == 0, false, (a and 0xF) + (v and 0xF) > 0xF, r > 0xFF); a = r and 0xFF }
            1 -> {
                val cy = if (flag(C)) 1 else 0; val r = a + v + cy
                setFlags(r and 0xFF == 0, false, (a and 0xF) + (v and 0xF) + cy > 0xF, r > 0xFF); a = r and 0xFF
            }
            2 -> { val r = a - v; setFlags(r and 0xFF == 0, true, (a and 0xF) < (v and 0xF), r < 0); a = r and 0xFF }
            3 -> {
                val cy = if (flag(C)) 1 else 0; val r = a - v - cy
                setFlags(r and 0xFF == 0, true, (a and 0xF) - (v and 0xF) - cy < 0, r < 0); a = r and 0xFF
            }
            4 -> { a = a and v; setFlags(a == 0, false, true, false) }
            5 -> { a = a xor v; setFlags(a == 0, false, false, false) }
            6 -> { a = a or v; setFlags(a == 0, false, false, false) }
            else -> { val r = a - v; setFlags(r and 0xFF == 0, true, (a and 0xF) < (v and 0xF), r < 0) } // CP
        }
    }

    private fun inc8(v: Int): Int {
        val r = (v + 1) and 0xFF
        setFlags(r == 0, false, v and 0xF == 0xF, flag(C)); return r
    }
    private fun dec8(v: Int): Int {
        val r = (v - 1) and 0xFF
        setFlags(r == 0, true, v and 0xF == 0, flag(C)); return r
    }
    private fun addHl(v: Int) {
        val r = hl + v
        setFlags(flag(Z), false, (hl and 0xFFF) + (v and 0xFFF) > 0xFFF, r > 0xFFFF); hl = r and 0xFFFF
    }
    private fun spPlusOffset(): Int {
        val off = fetch().toByte().toInt()
        setFlags(false, false, (sp and 0xF) + (off and 0xF) > 0xF, (sp and 0xFF) + (off and 0xFF) > 0xFF)
        return (sp + off) and 0xFFFF
    }

    private fun daa() {
        var r = a
        if (!flag(N)) {
            if (flag(C) || r > 0x99) { r += 0x60; setFlag(C, true) }
            if (flag(H) || (r and 0xF) > 9) r += 6
        } else {
            if (flag(C)) r -= 0x60
            if (flag(H)) r -= 6
        }
        a = r and 0xFF
        setFlag(Z, a == 0); setFlag(H, false)
    }

    private fun jr(taken: Boolean): Int {
        val off = fetch().toByte().toInt()
        if (!taken) return 8
        pc = (pc + off) and 0xFFFF; return 12
    }

    private fun execute(op: Int): Int {
        // LD r,r' and HALT
        if (op in 0x40..0x7F) {
            if (op == 0x76) {
                if (!ime && interrupts.pending() != 0) haltBug = true else halted = true
                return 4
            }
            val dst = (op shr 3) and 7; val src = op and 7
            setR(dst, getR(src))
            return if (dst == 6 || src == 6) 8 else 4
        }
        // ALU A,r
        if (op in 0x80..0xBF) {
            val src = op and 7
            alu((op shr 3) and 7, getR(src))
            return if (src == 6) 8 else 4
        }
        return when (op) {
            0x00 -> 4
            0x10 -> { fetch(); 4 } // STOP
            0x08 -> { val addr = fetch16(); write(addr, sp and 0xFF); write(addr + 1, sp shr 8); 20 }
            0x18 -> jr(true)
            0x20, 0x28, 0x30, 0x38 -> jr(condition((op shr 3) and 3))
            0x01, 0x11, 0x21, 0x31 -> { setRR(op shr 4, fetch16()); 12 }
            0x09, 0x19, 0x29, 0x39 -> { addHl(getRR(op shr 4)); 8 }
            0x02 -> { write(bc, a); 8 }
            0x12 -> { write(de, a); 8 }
            0x22 -> { write(hl, a); hl = hl + 1; 8 }
            0x32 -> { write(hl, a); hl = hl - 1; 8 }
            0x0A -> { a = read(bc); 8 }
            0x1A -> { a = read(de); 8 }
            0x2A -> { a = read(hl); hl = hl + 1; 8 }
            0x3A -> { a = read(hl); hl = hl - 1; 8 }
            0x03, 0x13, 0x23, 0x33 -> { setRR(op shr 4, getRR(op shr 4) + 1); 8 }
            0x0B, 0x1B, 0x2B, 0x3B -> { setRR(op shr 4, getRR(op shr 4) - 1); 8 }
            0x04, 0x0C, 0x14, 0x1C, 0x24, 0x2C, 0x34, 0x3C -> {
                val r = (op shr 3) and 7; setR(r, inc8(getR(r))); if (r == 6) 12 else 4
            }
            0x05, 0x0D, 0x15, 0x1D, 0x25, 0x2D, 0x35, 0x3D -> {
                val r = (op shr 3) and 7; setR(r, dec8(getR(r))); if (r == 6) 12 else 4
            }
            0x06, 0x0E, 0x16, 0x1E, 0x26, 0x2E, 0x36, 0x3E -> {
                val r = (op shr 3) and 7; setR(r, fetch()); if (r == 6) 12 else 8
            }
            0x07 -> { val cy = a shr 7; a = ((a shl 1) or cy) and 0xFF; setFlags(false, false, false, cy == 1); 4 }
            0x0F -> { val cy = a and 1; a = (a shr 1) or (cy shl 7); setFlags(false, false, false, cy == 1); 4 }
            0x17 -> { val cy = a shr 7; a = ((a shl 1) or (if (flag(C)) 1 else 0)) and 0xFF; setFlags(false, false, false, cy == 1); 4 }
            0x1F -> { val cy = a and 1; a = (a shr 1) or (if (flag(C)) 0x80 else 0); setFlags(false, false, false, cy == 1); 4 }
            0x27 -> { daa(); 4 }
            0x2F -> { a = a.inv() and 0xFF; setFlag(N, true); setFlag(H, true); 4 }
            0x37 -> { setFlag(N, false); setFlag(H, false); setFlag(C, true); 4 }
            0x3F -> { setFlag(N, false); setFlag(H, false); setFlag(C, !flag(C)); 4 }

            0xC0, 0xC8, 0xD0, 0xD8 -> if (condition((op shr 3) and 3)) { pc = pop(); 20 } else 8
            0xC9 -> { pc = pop(); 16 }
            0xD9 -> { pc = pop(); ime = true; 16 }
            0xC1, 0xD1, 0xE1 -> { setRR((op shr 4) and 3, pop()); 12 }
            0xF1 -> { af = pop(); 12 }
            0xC5, 0xD5, 0xE5 -> { push(getRR((op shr 4) and 3)); 16 }
            0xF5 -> { push(af); 16 }
            0xC2, 0xCA, 0xD2, 0xDA -> { val addr = fetch16(); if (condition((op shr 3) and 3)) { pc = addr; 16 } else 12 }
            0xC3 -> { pc = fetch16(); 16 }
            0xE9 -> { pc = hl; 4 }
            0xC4, 0xCC, 0xD4, 0xDC -> {
                val addr = fetch16()
                if (condition((op shr 3) and 3)) { push(pc); pc = addr; 24 } else 12
            }
            0xCD -> { val addr = fetch16(); push(pc); pc = addr; 24 }
            0xC6, 0xCE, 0xD6, 0xDE, 0xE6, 0xEE, 0xF6, 0xFE -> { alu((op shr 3) and 7, fetch()); 8 }
            0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF -> { push(pc); pc = op and 0x38; 16 }
            0xCB -> executeCb(fetch())
            0xE0 -> { write(0xFF00 + fetch(), a); 12 }
            0xF0 -> { a = read(0xFF00 + fetch()); 12 }
            0xE2 -> { write(0xFF00 + c, a); 8 }
            0xF2 -> { a = read(0xFF00 + c); 8 }
            0xEA -> { write(fetch16(), a); 16 }
            0xFA -> { a = read(fetch16()); 16 }
            0xE8 -> { sp = spPlusOffset(); 16 }
            0xF8 -> { hl = spPlusOffset(); 12 }
            0xF9 -> { sp = hl; 8 }
            0xF3 -> { ime = false; eiPending = false; 4 }
            0xFB -> { eiPending = true; 4 }
            else -> 4 // undefined opcodes behave like NOP here
        }
    }

    private fun executeCb(op: Int): Int {
        val r = op and 7
        val y = (op shr 3) and 7
        val isHl = r == 6
        when (op shr 6) {
            0 -> {
                val v = getR(r)
                val cy: Int
                val res: Int
                when (y) {
                    0 -> { cy = v shr 7; res = ((v shl 1) or cy) and 0xFF }                                   // RLC
                    1 -> { cy = v and 1; res = (v shr 1) or (cy shl 7) }                                      // RRC
                    2 -> { cy = v shr 7; res = ((v shl 1) or (if (flag(C)) 1 else 0)) and 0xFF }              // RL
                    3 -> { cy = v and 1; res = (v shr 1) or (if (flag(C)) 0x80 else 0) }                      // RR
                    4 -> { cy = v shr 7; res = (v shl 1) and 0xFF }                                           // SLA
                    5 -> { cy = v and 1; res = (v shr 1) or (v and 0x80) }                                    // SRA
                    6 -> { cy = 0; res = ((v shl 4) or (v shr 4)) and 0xFF }                                  // SWAP
                    else -> { cy = v and 1; res = v shr 1 }                                                   // SRL
                }
                setR(r, res)
                setFlags(res == 0, false, false, cy == 1)
                return if (isHl) 16 else 8
            }
            1 -> { // BIT
                setFlags(getR(r) and (1 shl y) == 0, false, true, flag(C))
                return if (isHl) 12 else 8
            }
            2 -> { setR(r, getR(r) and (1 shl y).inv()); return if (isHl) 16 else 8 } // RES
            else -> { setR(r, getR(r) or (1 shl y)); return if (isHl) 16 else 8 }     // SET
        }
    }

    companion object {
        const val Z = 0x80
        const val N = 0x40
        const val H = 0x20
        const val C = 0x10
    }
}
