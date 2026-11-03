package com.rough.snoremonitor.detection

import kotlin.math.abs

/**
 * Detects rhythmic snoring patterns from a stream of RMS decibel levels.
 *
 * Snoring typically exhibits:
 * 1. An amplitude spike 12-25 dB above background ambient noise.
 * 2. A periodicity of 2.0 to 5.5 seconds between inhalation peaks.
 * 3. Repetition: at least [requiredCycles] consecutive cycles to eliminate false triggers from
 *    single loud noises like coughs, room movement, or door closings.
 */
class SnoreDetector(
    private val requiredCycles: Int = 4,
    private val minPeriodMs: Long = 1800L,
    private val maxPeriodMs: Long = 5500L,
    var sensitivityDb: Float = 14.0f
) {
    private var ambientBaselineDb: Float = 40.0f
    private val peakTimestamps = mutableListOf<Long>()
    private var lastPeakTimeMs: Long = 0L

    /**
     * Process an RMS decibel reading taken at [timestampMs].
     * Returns true if a confirmed rhythmic snoring pattern is detected.
     */
    fun processRms(rmsDb: Float, timestampMs: Long): Boolean {
        // Adapt ambient baseline slowly if quiet
        if (rmsDb < ambientBaselineDb + 5.0f) {
            ambientBaselineDb = (0.95f * ambientBaselineDb) + (0.05f * rmsDb)
        }

        val isPeak = (rmsDb - ambientBaselineDb) >= sensitivityDb

        if (isPeak) {
            val elapsedSinceLastPeak = timestampMs - lastPeakTimeMs

            // Avoid registering multiple points within the same snoring breath (minimum 1.5s separation)
            if (elapsedSinceLastPeak >= 1500L) {
                lastPeakTimeMs = timestampMs

                if (peakTimestamps.isEmpty()) {
                    peakTimestamps.add(timestampMs)
                } else {
                    val prevPeak = peakTimestamps.last()
                    val period = timestampMs - prevPeak

                    if (period in minPeriodMs..maxPeriodMs) {
                        peakTimestamps.add(timestampMs)
                        if (peakTimestamps.size >= requiredCycles) {
                            peakTimestamps.clear()
                            return true
                        }
                    } else if (period > maxPeriodMs) {
                        // Rhythm broken - reset with current peak as new anchor
                        peakTimestamps.clear()
                        peakTimestamps.add(timestampMs)
                    }
                }
            }
        } else {
            // If too much time has passed since last peak, clear stale history
            if (lastPeakTimeMs > 0 && (timestampMs - lastPeakTimeMs) > (maxPeriodMs * 2)) {
                peakTimestamps.clear()
            }
        }

        return false
    }

    fun reset() {
        peakTimestamps.clear()
        lastPeakTimeMs = 0L
    }
}
