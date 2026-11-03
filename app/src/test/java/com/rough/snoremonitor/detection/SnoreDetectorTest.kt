package com.rough.snoremonitor.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoreDetectorTest {

    @Test
    fun testIsolatedLoudNoiseDoesNotTrigger() {
        val detector = SnoreDetector(requiredCycles = 4)
        var triggered = false

        // Baseline quiet
        for (t in 0..10) {
            triggered = detector.processRms(35.0f, t * 500L) || triggered
        }

        // Single loud noise at 6000ms (cough or door slam)
        triggered = detector.processRms(75.0f, 6000L) || triggered

        // Subsequent silence
        for (t in 13..25) {
            triggered = detector.processRms(35.0f, t * 500L) || triggered
        }

        assertFalse("Isolated loud noise should not trigger snore detection", triggered)
    }

    @Test
    fun testRhythmicSnoringTriggersAfterRequiredCycles() {
        val detector = SnoreDetector(requiredCycles = 4)
        var triggered = false

        // Establish 35dB baseline
        for (t in 0..5) {
            detector.processRms(35.0f, t * 500L)
        }

        // Simulate 4 snoring breaths spaced exactly 3000ms apart
        val snoreTimes = listOf(3000L, 6000L, 9000L, 12000L)
        for (time in snoreTimes) {
            triggered = detector.processRms(65.0f, time)
        }

        assertTrue("Rhythmic snoring peaks should trigger detection", triggered)
    }

    @Test
    fun testLowVolumeBackgroundNoiseDoesNotTrigger() {
        val detector = SnoreDetector(requiredCycles = 4)
        var triggered = false

        // Continuous 32dB gentle room hum
        for (t in 0..50) {
            triggered = detector.processRms(32.0f, t * 500L) || triggered
        }

        assertFalse("Low continuous room noise should not trigger", triggered)
    }
}
