package com.ble_finder.data

import android.bluetooth.le.ScanResult
import com.ble_finder.utils.DistanceCalculator
import kotlinx.coroutines.flow.Flow

class DeviceRepository(private val savedDeviceDao: SavedDeviceDao) {
    val allSavedDevices: Flow<List<SavedDevice>> = savedDeviceDao.getAllDevices()

    suspend fun saveDevice(scanResult: ScanResult) {
        try {
            val distance = DistanceCalculator.calculateDistance(scanResult.rssi, scanResult.txPower ?: -59)
            val device = SavedDevice(
                macAddress = scanResult.device.address,
                name = scanResult.device.name ?: "Unknown Device",
                lastKnownRssi = scanResult.rssi,
                lastSeenTimestamp = System.currentTimeMillis(),
                isInRange = true,
                notificationEnabled = true,
                notificationThresholdDistance = distance * 1.5 // Set initial threshold to 1.5x current distance
            )
            savedDeviceDao.insertDevice(device)
        } catch (e: Exception) {
            // Handle any errors that might occur during saving
            throw Exception("Failed to save device: ${e.message}")
        }
    }

    suspend fun deleteDevice(device: SavedDevice) {
        savedDeviceDao.deleteDevice(device)
    }

    suspend fun updateDeviceStatus(scanResult: ScanResult) {
        val distance = DistanceCalculator.calculateDistance(scanResult.rssi, scanResult.txPower ?: -59)
        val device = savedDeviceDao.getDeviceByMacAddress(scanResult.device.address)
        
        device?.let {
            val isInRange = distance <= it.notificationThresholdDistance
            savedDeviceDao.updateDeviceStatus(
                macAddress = scanResult.device.address,
                rssi = scanResult.rssi,
                timestamp = System.currentTimeMillis(),
                isInRange = isInRange
            )
        }
    }

    suspend fun updateDeviceStatusWithLocation(scanResult: ScanResult, latitude: Double, longitude: Double) {
        val distance = DistanceCalculator.calculateDistance(scanResult.rssi, scanResult.txPower ?: -59)
        val device = savedDeviceDao.getDeviceByMacAddress(scanResult.device.address)
        device?.let {
            val isInRange = distance <= it.notificationThresholdDistance
            savedDeviceDao.updateDeviceStatusWithLocation(
                macAddress = scanResult.device.address,
                rssi = scanResult.rssi,
                timestamp = System.currentTimeMillis(),
                isInRange = isInRange,
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    suspend fun toggleNotifications(device: SavedDevice, enabled: Boolean) {
        savedDeviceDao.updateNotificationSetting(device.macAddress, enabled)
    }

    suspend fun updateNotificationThreshold(device: SavedDevice, distance: Double) {
        savedDeviceDao.updateNotificationThreshold(device.macAddress, distance)
    }

    suspend fun getDeviceByMacAddress(macAddress: String): SavedDevice? {
        return savedDeviceDao.getDeviceByMacAddress(macAddress)
    }
} 