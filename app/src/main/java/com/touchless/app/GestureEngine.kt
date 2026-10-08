package com.touchless.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.hypot

/** Tracks the center of any visible hand. Camera frames are never retained. */
class GestureEngine {
    enum class State { IDLE, HAND_DETECTED, ARMED, TRACKING, GESTURE_CONFIRMED, COOLDOWN }
    enum class Action { SWIPE_UP, HOME }
    data class Result(val state: State, val reason: String, val action: Action? = null, val movementPercent: Int = 0)
    private data class Point(val time: Long, val x: Float, val y: Float)
    private val points = ArrayDeque<Point>()
    private var lastCenter: Point? = null
    private var cooldownUntil = 0L
    private var waitingForRearm = false
    private var steadyFrames = 0
    private var missedFrames = 0
    private var fistSince = 0L
    private var fistFrames = 0
    private var fistAnchor: Point? = null
    private var fistNeedsRelease = false

    fun reset() {
        points.clear(); lastCenter = null
        cooldownUntil = 0L; waitingForRearm = false; steadyFrames = 0; missedFrames = 0
        fistSince = 0L; fistFrames = 0; fistAnchor = null; fistNeedsRelease = false
    }

    private fun clearTracking() {
        points.clear(); lastCenter = null; waitingForRearm = false; steadyFrames = 0; missedFrames = 0
        fistSince = 0L; fistFrames = 0; fistAnchor = null
    }

    fun analyze(hands: List<List<NormalizedLandmark>>, now: Long, settings: GestureSettings = GestureSettings(),
                deviceMoving: Boolean = false): Result {
        if (deviceMoving) {
            clearTracking()
            return Result(State.IDLE, "手机移动中，已暂停手势识别")
        }
        val centers = hands.mapNotNull { palmCenter(it, now) }
        if (centers.isEmpty()) {
            missedFrames++
            if (missedFrames <= 2 && lastCenter != null && now - lastCenter!!.time <= 250L)
                return Result(State.HAND_DETECTED, "手部短暂离开画面")
            clearTracking()
            return Result(State.IDLE, "未检测到手")
        }
        missedFrames = 0

        // If two hands are visible, follow the one nearest the previous palm.
        val previous = lastCenter
        val current = if (previous == null) centers.first() else
            centers.minBy { hypot(it.x - previous.x, it.y - previous.y) }
        if (previous != null && hypot(current.x - previous.x, current.y - previous.y) > .35f) {
            points.clear(); steadyFrames = 0; waitingForRearm = false
        }
        lastCenter = current

        if (!settings.homeGestureEnabled) fistNeedsRelease = false
        if (now < cooldownUntil) return Result(State.COOLDOWN, "等待下次动作")
        if (fistNeedsRelease) {
            if (isClosedFist(hands.first())) return Result(State.COOLDOWN, "张开手后可继续操作")
            fistNeedsRelease = false; points.clear()
        }
        if (waitingForRearm) {
            val movement = if (previous == null) 1f else hypot(current.x - previous.x, current.y - previous.y)
            steadyFrames = if (movement < .035f) steadyFrames + 1 else 0
            if (steadyFrames < 2) return Result(State.COOLDOWN, "请让手停稳后再做下一次")
            waitingForRearm = false; steadyFrames = 0; points.clear()
        }

        // HOME is optional. A still fist held in front of the camera triggers it;
        // a moving fist can still make the normal upward hand gesture.
        val fistRecognized = settings.homeGestureEnabled && isClosedFist(hands.first())
        if (fistRecognized) {
            val anchor = fistAnchor
            if (anchor == null) points.clear()
            val stableRadius = .05f + settings.distancePercent.coerceIn(2, 20) / 200f
            if (anchor == null || hypot(current.x - anchor.x, current.y - anchor.y) > stableRadius) {
                fistSince = now; fistFrames = 1; fistAnchor = current
            } else fistFrames++
            val holdMs = 750L + (settings.recognitionTimeMs.coerceIn(60, 400) - 100) / 2
            if (fistFrames >= 4 && now - fistSince >= holdMs) {
                cooldownUntil = now + settings.intervalMs.coerceIn(1000, 2000)
                fistSince = 0L; fistFrames = 0; fistAnchor = null
                fistNeedsRelease = true
                waitingForRearm = true; steadyFrames = 0
                points.clear()
                return Result(State.GESTURE_CONFIRMED, "已识别握拳", Action.HOME)
            }
        } else {
            fistSince = 0L; fistFrames = 0; fistAnchor = null
        }

        points.addLast(current)
        while (points.size > 10 || (points.isNotEmpty() && now - points.first().time > 700)) points.removeFirst()
        val state = when {
            points.size == 1 -> State.HAND_DETECTED
            points.size < 3 -> State.ARMED
            else -> State.TRACKING
        }
        if (points.size < 3) return Result(state, "正在观察手部移动")

        val first = points.first()
        val dy = current.y - first.y
        val dx = current.x - first.x
        val movementPercent = (abs(dy) * 100f).toInt()
        val duration = current.time - first.time
        if (duration < settings.recognitionTimeMs) return Result(state, "正在确认动作", movementPercent = movementPercent)
        val sensitivityFactor = 1.25f - settings.sensitivity.coerceIn(0, 100) * .005f
        val minDistance = settings.distancePercent.coerceIn(2, 20) / 100f * sensitivityFactor
        if (dy > minDistance) {
            points.clear(); points.addLast(current)
            return Result(state, "手部向下不执行操作", movementPercent = movementPercent)
        }
        if (dy >= 0f) return Result(state,
            if (fistRecognized && dy == 0f) "正在确认握拳" else "手部向下不执行操作",
            movementPercent = movementPercent)
        val upwardDistance = -dy
        if (upwardDistance < minDistance) return Result(state, if (fistRecognized) "正在确认握拳" else "移动距离不足", movementPercent = movementPercent)
        if (upwardDistance < abs(dx) * .5f)
            return Result(state, "横向移动不执行操作", movementPercent = movementPercent)
        if (upwardDistance * 1000f / duration < .07f * sensitivityFactor)
            return Result(state, "移动速度过慢", movementPercent = movementPercent)

        val samples = points.toList()
        var matching = 0; var meaningful = 0
        for (i in 1 until samples.size) {
            val step = samples[i].y - samples[i - 1].y
            if (abs(step) >= .004f) { meaningful++; if (step * dy > 0f) matching++ }
        }
        if (meaningful < 2 || matching.toFloat() / meaningful < .55f)
            return Result(state, "移动方向不稳定", movementPercent = movementPercent)

        points.clear(); waitingForRearm = true; steadyFrames = 0
        cooldownUntil = now + settings.intervalMs.coerceIn(1000, 2000)
        return Result(State.GESTURE_CONFIRMED, "已识别手部向上移动", Action.SWIPE_UP, movementPercent)
    }

    private fun isClosedFist(hand: List<NormalizedLandmark>): Boolean {
        if (hand.size < 21) return false
        val required = intArrayOf(0, 5, 6, 8, 9, 10, 12, 13, 14, 16, 17, 18, 20)
        if (required.any { i -> !hand[i].x().isFinite() || !hand[i].y().isFinite() ||
                hand[i].x() !in 0f..1f || hand[i].y() !in 0f..1f }) return false
        val wrist = hand[0]
        val indexBase = hand[5]
        val pinkyBase = hand[17]
        if (hypot(indexBase.x() - pinkyBase.x(), indexBase.y() - pinkyBase.y()) < .06f ||
            hypot(hand[9].x() - wrist.x(), hand[9].y() - wrist.y()) < .05f) return false
        var curled = 0
        for ((base, middle, tip) in listOf(Triple(5, 6, 8), Triple(9, 10, 12), Triple(13, 14, 16), Triple(17, 18, 20))) {
            val mcp = hand[base]; val pip = hand[middle]; val fingerTip = hand[tip]
            val middleDistance = hypot(pip.x() - mcp.x(), pip.y() - mcp.y())
            val tipDistance = hypot(fingerTip.x() - mcp.x(), fingerTip.y() - mcp.y())
            val wristToPip = hypot(pip.x() - wrist.x(), pip.y() - wrist.y())
            val wristToTip = hypot(fingerTip.x() - wrist.x(), fingerTip.y() - wrist.y())
            if (middleDistance > .025f && tipDistance <= middleDistance * .90f &&
                wristToTip < wristToPip * .96f) curled++
        }
        return curled == 4
    }

    private fun palmCenter(hand: List<NormalizedLandmark>, time: Long): Point? {
        // The model normally returns 21 points. The median of points inside the
        // picture also works when wrist or fingers are outside the camera frame.
        val visible = hand.filter { it.x().isFinite() && it.y().isFinite() && it.x() in 0f..1f && it.y() in 0f..1f }
        if (visible.size < 4) return null
        val x = visible.map { it.x() }.sorted()[visible.size / 2]
        val y = visible.map { it.y() }.sorted()[visible.size / 2]
        return Point(time, x, y)
    }
}
