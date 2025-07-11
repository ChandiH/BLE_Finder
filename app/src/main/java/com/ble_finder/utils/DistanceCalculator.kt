package com.ble_finder.utils

import kotlin.math.pow

object DistanceCalculator {
    // Environmental factor - can be adjusted based on the environment
    // 2.0 for free space
    // 3.0 for indoor with walls
    // 4.0 for highly obstructed environment
    private const val N = 2.5 // Default environmental factor for indoor/outdoor mixed environment

    /**
     * Calculate distance using the Log-distance path loss model
     * @param rssi Current RSSI value
     * @param txPower Measured power at 1 meter (if available) or default calibrated value
     * @return Estimated distance in meters
     */
    fun calculateDistance(rssi: Int, txPower: Int = -59): Double {
        if (rssi == 0) return -1.0 // Invalid RSSI

        val ratio = (txPower - rssi) / (10.0 * N)
        return 10.0.pow(ratio)
    }

    /**
     * Get a human-readable distance string
     */
    fun getReadableDistance(distance: Double): String {
        return when {
            distance < 0 -> "Unknown"
            distance < 1 -> "%.2f m (Very Close)".format(distance)
            distance < 3 -> "%.2f m (Close)".format(distance)
            distance < 10 -> "%.2f m (Medium)".format(distance)
            else -> "%.2f m (Far)".format(distance)
        }
    }
} 