package com.touchless.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureEngineTest {
    private fun hand(y: Float): List<NormalizedLandmark> =
        List(21) { NormalizedLandmark.create(.5f, y, 0f) }

    private fun fist(): List<NormalizedLandmark> {
        val locations = MutableList(21) { .5f to .53f }
        locations[0] = .5f to .75f
        for ((mcp, pip, tip, x) in listOf(
            listOf(5, 6, 8, 40), listOf(9, 10, 12, 46),
            listOf(13, 14, 16, 54), listOf(17, 18, 20, 60)
        )) {
            val px = x / 100f
            locations[mcp] = px to .55f
            locations[pip] = px to .45f
            locations[tip] = px to .53f
        }
        return locations.map { (x, y) -> NormalizedLandmark.create(x, y, 0f) }
    }

    @Test fun handMovingDownDoesNotTrigger() {
        val engine = GestureEngine()
        val samples = listOf(.30f, .35f, .40f, .45f)
        val results = samples.mapIndexed { i, y -> engine.analyze(listOf(hand(y)), i * 100L) }
        assertEquals(0, results.count { it.action != null })
        assertNull(engine.analyze(listOf(hand(.50f)), 400).action)
    }

    @Test fun handMovingUpTriggersScreenUpSwipe() {
        val engine = GestureEngine()
        val samples = listOf(.70f, .65f, .60f, .55f)
        val results = samples.mapIndexed { i, y -> engine.analyze(listOf(hand(y)), i * 100L) }
        assertEquals(1, results.count { it.action == GestureEngine.Action.SWIPE_UP })
    }

    @Test fun upwardMovementAfterDownwardResetTriggers() {
        val engine = GestureEngine()
        val positions = listOf(.40f, .45f, .50f, .46f, .42f)
        val actions = positions.mapIndexed { i, y -> engine.analyze(listOf(hand(y)), i * 100L).action }
        assertEquals(1, actions.count { it == GestureEngine.Action.SWIPE_UP })
    }

    @Test fun stationaryHandDoesNotTrigger() {
        val engine = GestureEngine()
        repeat(10) { i -> assertNull(engine.analyze(listOf(hand(.5f)), i * 100L).action) }
    }

    @Test fun modestWholeHandMovementTriggers() {
        val engine = GestureEngine()
        val samples = listOf(.60f, .57f, .54f, .51f)
        val results = samples.mapIndexed { i, y -> engine.analyze(listOf(hand(y)), i * 100L) }
        assertEquals(1, results.count { it.action == GestureEngine.Action.SWIPE_UP })
    }

    @Test fun partialVisibleLandmarksCanStillTrigger() {
        val engine = GestureEngine()
        fun partial(y: Float) = List(21) { i ->
            if (i < 5) NormalizedLandmark.create(.5f, y, 0f)
            else NormalizedLandmark.create(1.3f, y, 0f)
        }
        val samples = listOf(.65f, .61f, .57f)
        val results = samples.mapIndexed { i, y -> engine.analyze(listOf(partial(y)), i * 80L) }
        assertEquals(GestureEngine.Action.SWIPE_UP, results.last().action)
    }

    @Test fun horizontalWaveDoesNotTriggerAnyAction() {
        val engine = GestureEngine()
        repeat(5) { i ->
            val hand = List(21) { NormalizedLandmark.create(.30f + i * .08f, .50f - i * .01f, 0f) }
            assertNull(engine.analyze(listOf(hand), i * 80L).action)
        }
    }

    @Test fun diagonalUpwardMovementStillSwipes() {
        val engine = GestureEngine()
        val actions = (0..3).map { i ->
            val moved = List(21) { NormalizedLandmark.create(.30f + i * .07f, .55f - i * .04f, 0f) }
            engine.analyze(listOf(moved), i * 100L).action
        }
        assertEquals(1, actions.count { it == GestureEngine.Action.SWIPE_UP })
    }

    @Test fun steadyClosedFistReturnsHomeOnce() {
        val engine = GestureEngine()
        val settings = GestureSettings(homeGestureEnabled = true)
        val actions = (0..25).map { i -> engine.analyze(listOf(fist()), i * 100L, settings).action }
        assertEquals(1, actions.count { it == GestureEngine.Action.HOME })
    }

    @Test fun recognitionTimeAlsoChangesFistHoldTime() {
        val normal = GestureEngine()
        val slower = GestureEngine()
        val normalSettings = GestureSettings(homeGestureEnabled = true)
        val slowerSettings = normalSettings.copy(recognitionTimeMs = 400)
        val normalActions = (0..8).map { normal.analyze(listOf(fist()), it * 100L, normalSettings).action }
        val slowerActions = (0..8).map { slower.analyze(listOf(fist()), it * 100L, slowerSettings).action }
        assertEquals(1, normalActions.count { it == GestureEngine.Action.HOME })
        assertEquals(0, slowerActions.count { it == GestureEngine.Action.HOME })
    }

    @Test fun closedFistDoesNotReturnHomeWhenSwitchIsOff() {
        val engine = GestureEngine()
        repeat(15) { i -> assertNull(engine.analyze(listOf(fist()), i * 100L).action) }
    }

    @Test fun movingFistUpStillSwipesWhenHomeSwitchIsOn() {
        val engine = GestureEngine()
        val settings = GestureSettings(homeGestureEnabled = true)
        val results = (0..3).map { i ->
            val moved = fist().map { NormalizedLandmark.create(it.x(), it.y() - i * .04f, 0f) }
            engine.analyze(listOf(moved), i * 100L, settings)
        }
        assertEquals(1, results.count { it.action == GestureEngine.Action.SWIPE_UP })
        assertEquals(0, results.count { it.action == GestureEngine.Action.HOME })
    }

    @Test fun cooldownPersistsAfterHandLeavesFrame() {
        val engine = GestureEngine()
        val settings = GestureSettings(intervalMs = 2000)
        listOf(.60f, .55f, .50f).forEachIndexed { i, y -> engine.analyze(listOf(hand(y)), i * 100L, settings) }
        engine.analyze(emptyList(), 400L, settings)
        engine.analyze(emptyList(), 500L, settings)
        engine.analyze(emptyList(), 600L, settings)
        val duringCooldown = listOf(.60f, .55f, .50f).mapIndexed { i, y ->
            engine.analyze(listOf(hand(y)), 900L + i * 100L, settings).action
        }
        assertEquals(0, duringCooldown.count { it != null })
    }

    @Test fun adjustableDistanceControlsSmallMovement() {
        val handPositions = listOf(.50f, .49f, .48f)
        val sensitive = GestureEngine()
        val normal = GestureEngine()
        val close = GestureSettings(distancePercent = 2, recognitionTimeMs = 60, sensitivity = 100)
        val far = GestureSettings(distancePercent = 20, recognitionTimeMs = 60, sensitivity = 0)
        assertEquals(GestureEngine.Action.SWIPE_UP,
            handPositions.mapIndexed { i, y -> sensitive.analyze(listOf(hand(y)), i * 80L, close) }.last().action)
        assertNull(handPositions.mapIndexed { i, y -> normal.analyze(listOf(hand(y)), i * 80L, far) }.last().action)
    }

    @Test fun phoneMotionSuppressesApparentHandSwipe() {
        val engine = GestureEngine()
        val actions = listOf(.70f, .65f, .60f, .55f).mapIndexed { i, y ->
            engine.analyze(listOf(hand(y)), i * 100L, deviceMoving = true).action
        }
        assertEquals(0, actions.count { it != null })
    }

    @Test fun threeCurledFingersAreNotAClosedFist() {
        val engine = GestureEngine()
        val relaxed = fist().toMutableList()
        relaxed[20] = NormalizedLandmark.create(.60f, .30f, 0f)
        val settings = GestureSettings(homeGestureEnabled = true)
        repeat(15) { i -> assertNull(engine.analyze(listOf(relaxed), i * 100L, settings).action) }
    }
}
