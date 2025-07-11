package com.ble_finder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedDeviceDao {
    @Query("SELECT * FROM saved_devices")
    fun getAllDevices(): Flow<List<SavedDevice>>

    @Query("SELECT * FROM saved_devices WHERE macAddress = :macAddress")
    suspend fun getDeviceByMacAddress(macAddress: String): SavedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedDevice)

    @Delete
    suspend fun deleteDevice(device: SavedDevice)

    @Query("UPDATE saved_devices SET lastKnownRssi = :rssi, lastSeenTimestamp = :timestamp, isInRange = :isInRange WHERE macAddress = :macAddress")
    suspend fun updateDeviceStatus(macAddress: String, rssi: Int, timestamp: Long, isInRange: Boolean)

    @Query("UPDATE saved_devices SET lastKnownRssi = :rssi, lastSeenTimestamp = :timestamp, isInRange = :isInRange, lastKnownLatitude = :latitude, lastKnownLongitude = :longitude WHERE macAddress = :macAddress")
    suspend fun updateDeviceStatusWithLocation(macAddress: String, rssi: Int, timestamp: Long, isInRange: Boolean, latitude: Double, longitude: Double)

    @Query("UPDATE saved_devices SET notificationEnabled = :enabled WHERE macAddress = :macAddress")
    suspend fun updateNotificationSetting(macAddress: String, enabled: Boolean)

    @Query("UPDATE saved_devices SET notificationThresholdDistance = :distance WHERE macAddress = :macAddress")
    suspend fun updateNotificationThreshold(macAddress: String, distance: Double)
} 