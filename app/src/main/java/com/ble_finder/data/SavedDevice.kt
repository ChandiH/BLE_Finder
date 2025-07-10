package com.ble_finder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_devices")
data class SavedDevice(
    @PrimaryKey
    val macAddress: String,
    val name: String,
    val lastKnownRssi: Int = 0,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isInRange: Boolean = true,
    val notificationEnabled: Boolean = true,
    val notificationThresholdDistance: Double = 10.0, // Default 10 meters
    val lastKnownLatitude: Double = 0.0,
    val lastKnownLongitude: Double = 0.0
//    val distance: Double,
//    val deviceType: String,
) 