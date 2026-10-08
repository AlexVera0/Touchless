package com.touchless.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerEngineTest {
    private fun pose(indexY: Float, x: Float = .45f): List<NormalizedLandmark> {
        val points = MutableList(21) { .5f to .55f }
        points[0] = .5f to .8f
        points[5] = x to .55f; points[6] = x to .45f; points[8] = x to indexY
        for ((mcp, pip, tip, px) in listOf(
            listOf(9, 10, 12, 50), listOf(13, 14, 16, 55), listOf(17, 18, 20, 60))) {
            val fingerX = px / 100f
            points[mcp] = fingerX to .55f
            points[pip] = fingerX to .46f
            points[tip] = fingerX to .54f
        }
        return points.map { (px, py) -> NormalizedLandmark.create(px, py, 0f) }
    }

    @Test fun oneTapNeedsReleaseBeforeAnother() {
        val engine = PointerEngine()
        val results = listOf(.30f, .30f, .30f, .42f, .42f, .42f, .30f, .30f, .42f, .42f)
            .mapIndexed { i, y -> engine.analyze(pose(y), i * 100L) }
        assertTrue(results[2].visible)
        assertEquals(2, results.count { it.click })
    }

    @Test fun movingPhoneHidesCursor() {
        val engine = PointerEngine()
        repeat(3) { engine.analyze(pose(.30f), it * 100L) }
        assertFalse(engine.analyze(pose(.30f), 400L, true).visible)
    }
}
