package dev.kgameboy.core.video

/** Receives a finished 160x144 frame of ARGB pixels. The array is reused between frames. */
fun interface VideoSink {
    fun onFrame(pixels: IntArray)
}
