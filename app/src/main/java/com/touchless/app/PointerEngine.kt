package com.touchless.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

/** Processes one-index-finger poses independently from whole-hand gestures. */
class PointerEngine {
    data class Result(val visible: Boolean, val x: Float = 0f, val y: Float = 0f,
                      val click: Boolean = false, val active: Boolean = false)

    private var smoothX = 0f
    private var smoothY = 0f
    private var visibleFrames = 0
    private var armed = false
    private var bentFrames = 0
    private var lastClick = 0L

    fun reset() {
        visibleFrames = 0; armed = false; bentFrames = 0
    }

    fun analyze(hand: List<NormalizedLandmark>?, now: Long, deviceMoving: Boolean = false): Result {
        val required = intArrayOf(5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18, 20)
        if (deviceMoving || hand == null || hand.size < 21 || required.any { i ->
                !hand[i].x().isFinite() || !hand[i].y().isFinite() ||
                    hand[i].x() !in -.15f..1.15f || hand[i].y() !in -.15f..1.15f
            }) {
            reset(); return Result(false)
        }
        fun distance(a: Int, b: Int) = hypot(hand[a].x() - hand[b].x(), hand[a].y() - hand[b].y())
        val palmWidth = distance(5, 17)
        val indexBase = distance(5, 6)
        if (palmWidth < .055f || indexBase < .022f) { reset(); return Result(false) }
        // All three other fingers must be folded. This pose reserves the hand for pointer mode.
        for ((mcp, pip, tip) in listOf(Triple(9, 10, 12), Triple(13, 14, 16), Triple(17, 18, 20))) {
            if (distance(mcp, tip) > distance(mcp, pip) * 1.45f) { reset(); return Result(false) }
        }
        val extension = distance(5, 8) / indexBase
        if (extension < 1.12f || extension > 3.8f) { reset(); return Result(false) }
        val x = ((hand[8].x() - .08f) / .84f).coerceIn(0f, 1f)
        val y = ((hand[8].y() - .10f) / .80f).coerceIn(0f, 1f)
        if (visibleFrames == 0) { smoothX = x; smoothY = y }
        else { smoothX = smoothX * .58f + x * .42f; smoothY = smoothY * .58f + y * .42f }
        visibleFrames++
        if (visibleFrames < 3) return Result(false, active = true)

        // A click is a deliberate short bend followed by release. The motion gate
        // avoids interpreting a travelling fingertip or one noisy frame as a tap.
        val bent = extension < 1.52f
        if (!bent && extension > 1.78f) { armed = true; bentFrames = 0 }
        if (bent && armed) bentFrames++ else if (!bent) bentFrames = 0
        val click = bentFrames >= 2 && armed && now - lastClick >= 500L &&
            hypot(x - smoothX, y - smoothY) < .075f
        if (click) { armed = false; lastClick = now; bentFrames = 0 }
        return Result(true, smoothX, smoothY, click, true)
    }
}
