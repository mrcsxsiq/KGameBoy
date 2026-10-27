@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.kgameboy.web

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toInt

// ---- files ----

@JsFun(
    """(onFile) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.gb,.gbc';
        input.onchange = () => {
            const f = input.files[0];
            if (f) f.arrayBuffer().then(b => onFile(f.name, new Uint8Array(b)));
        };
        input.click();
    }"""
)
private external fun pickFileJs(onFile: (JsString, JsAny) -> Unit)

@JsFun("(url, onBytes) => { fetch(url).then(r => r.arrayBuffer()).then(b => onBytes(new Uint8Array(b))); }")
private external fun fetchBytesJs(url: String, onBytes: (JsAny) -> Unit)

@JsFun("(a) => a.length")
private external fun jsLength(a: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun jsByte(a: JsAny, i: Int): Int

private fun JsAny.toByteArray(): ByteArray {
    val n = jsLength(this)
    return ByteArray(n) { jsByte(this, it).toByte() }
}

/** Opens the browser's file picker and delivers the chosen file's name and bytes. */
fun pickRom(onRom: (name: String, bytes: ByteArray) -> Unit) =
    pickFileJs { name, data -> onRom(name.toString(), data.toByteArray()) }

/** Downloads a file served next to the page. */
fun fetchBytes(url: String, onBytes: (ByteArray) -> Unit) =
    fetchBytesJs(url) { onBytes(it.toByteArray()) }

// ---- audio (Web Audio API) ----

@JsFun("() => { const C = window.AudioContext || window.webkitAudioContext; return new C({ sampleRate: 44100 }); }")
private external fun audioCreate(): JsAny

@JsFun("(ctx) => { if (ctx.state !== 'running') ctx.resume(); }")
private external fun audioResume(ctx: JsAny)

@JsFun("(n) => new Float32Array(n)")
private external fun newFloatArray(n: Int): JsAny

@JsFun("(a, i, v) => { a[i] = v; }")
private external fun floatSet(a: JsAny, i: Int, v: Float)

/**
 * Schedules one block of stereo audio right after the previous one. Returns false (and plays
 * nothing) when more than 200 ms is already queued, which keeps latency from growing.
 */
@JsFun(
    """(ctx, left, right, n) => {
        const now = ctx.currentTime;
        const start = Math.max(now + 0.02, window.__kgbNext || 0);
        if (start - now > 0.2) return false;
        const buffer = ctx.createBuffer(2, n, 44100);
        buffer.copyToChannel(left, 0);
        buffer.copyToChannel(right, 1);
        const source = ctx.createBufferSource();
        source.buffer = buffer;
        source.connect(ctx.destination);
        source.start(start);
        window.__kgbNext = start + buffer.duration;
        return true;
    }"""
)
private external fun audioSchedule(ctx: JsAny, left: JsAny, right: JsAny, n: Int): Boolean

/** Plays interleaved 16-bit stereo samples through a Web Audio context. */
class WebAudio {
    private val ctx: JsAny = audioCreate()

    /** Browsers keep audio suspended until a user gesture; call this from input handlers. */
    fun resume() = audioResume(ctx)

    fun push(samples: ShortArray, count: Int) {
        val frames = count / 2
        if (frames == 0) return
        val left = newFloatArray(frames)
        val right = newFloatArray(frames)
        for (i in 0 until frames) {
            floatSet(left, i, samples[i * 2] / 32768f)
            floatSet(right, i, samples[i * 2 + 1] / 32768f)
        }
        audioSchedule(ctx, left, right, frames)
    }
}
